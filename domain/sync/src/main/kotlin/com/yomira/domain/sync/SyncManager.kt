package com.yomira.domain.sync

import com.yomira.core.database.dao.SyncDao
import com.yomira.core.database.entity.SyncStateEntity
import com.yomira.data.sync.SyncApiException
import com.yomira.data.sync.SyncNotAuthenticatedException
import com.yomira.data.sync.SyncPayload
import com.yomira.data.sync.SyncRemoteStore
import com.yomira.data.sync.SyncWriteConflictException
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncResult {
    data class Success(
        val payload: SyncPayload,
        /** True when a remote document existed and was merged. */
        val downloaded: Boolean,
        /** The merged document is always written back, so this is true on success. */
        val uploaded: Boolean,
        val syncedAt: Long,
    ) : SyncResult

    /** The user has not connected Google Drive; background work exits quietly. */
    data object NotSignedIn : SyncResult

    data class Failure(
        val message: String,
        val retryable: Boolean,
        val cause: Throwable? = null,
    ) : SyncResult
}

/**
 * Two-way sync orchestrator.
 *
 * 1. Read the local Room projection.
 * 2. Download `yomira_sync.json` from `drive.appdata` (if present).
 * 3. Merge with [ConflictResolutionEngine] using timestamp-based LWW.
 * 4. Upload the merged document with optimistic concurrency (ETag).
 * 5. Persist the merged state to Room and advance the local sync version.
 *
 * Upload happens before the local write so a failed network call never replaces the
 * user's local state with a partially synced document. If another device wins the
 * race, the whole cycle is retried against the fresh remote revision.
 */
@Singleton
class SyncManager @Inject constructor(
    private val localStore: SyncLocalStore,
    private val remoteStore: SyncRemoteStore,
    private val merger: ConflictResolutionEngine,
    private val syncDao: SyncDao,
    private val identity: SyncIdentityStore,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock { performSync() }

    private suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        var lastConflict: SyncWriteConflictException? = null

        repeat(MAX_WRITE_ATTEMPTS) {
            try {
                val local = localStore.read()
                val remote = remoteStore.fetch()
                val remotePayload = remote?.additionalPayloads?.fold(remote.payload) { accumulated, duplicate ->
                    merger.merge(accumulated, duplicate)
                }
                val merged = merger.merge(
                    local = local,
                    remote = remotePayload,
                    mergedAt = System.currentTimeMillis(),
                )

                remoteStore.push(merged, remote?.revision)
                localStore.write(merged)
                recordSync(merged.version)

                return@withContext SyncResult.Success(
                    payload = merged,
                    downloaded = remote != null,
                    uploaded = true,
                    syncedAt = merged.generatedAt,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (auth: SyncNotAuthenticatedException) {
                return@withContext SyncResult.NotSignedIn
            } catch (conflict: SyncWriteConflictException) {
                lastConflict = conflict
                // Re-read local and remote, then merge again against the new revision.
            } catch (api: SyncApiException) {
                return@withContext SyncResult.Failure(
                    message = api.message ?: "Google Drive request failed",
                    retryable = api.retryable,
                    cause = api,
                )
            } catch (error: IOException) {
                return@withContext SyncResult.Failure(
                    message = error.message ?: "Network error while syncing",
                    retryable = true,
                    cause = error,
                )
            } catch (error: Exception) {
                return@withContext SyncResult.Failure(
                    message = error.message ?: "Sync failed",
                    retryable = false,
                    cause = error,
                )
            }
        }

        SyncResult.Failure(
            message = lastConflict?.message ?: "Could not resolve a concurrent Drive update",
            retryable = true,
            cause = lastConflict,
        )
    }

    private suspend fun recordSync(version: Long) {
        val deviceId = identity.deviceId()
        val current = syncDao.getByDeviceId(deviceId)
        val nextVersion = maxOf(current?.version ?: 0L, version)
        syncDao.upsert(
            SyncStateEntity(
                deviceId = deviceId,
                lastSyncedAt = Date(System.currentTimeMillis()),
                version = nextVersion,
            ),
        )
    }

    private companion object {
        const val MAX_WRITE_ATTEMPTS = 3
    }
}
