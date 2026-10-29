package com.yomira.app.settings

import com.yomira.core.database.dao.SyncDao
import com.yomira.domain.sync.SyncIdentityStore
import com.yomira.domain.sync.SyncManager
import com.yomira.domain.sync.SyncResult
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Testable boundary around cache owners and the authenticated Drive transport. */
interface SettingsMaintenanceServices {
    val backfill: Flow<com.yomira.feature.settings.MetadataBackfillState>
        get() = kotlinx.coroutines.flow.flowOf(com.yomira.feature.settings.MetadataBackfillState())
    suspend fun startBackfill() {}
    fun cancelBackfill() {}
    val lastSyncedAt: Flow<Long?>
    suspend fun cacheSize(): Long
    suspend fun clearCache()
    suspend fun sync(): SyncResult
}

class DefaultSettingsMaintenanceServices @Inject constructor(
    private val cache: CacheManager,
    private val syncManager: SyncManager,
    private val syncDao: SyncDao,
    private val identity: SyncIdentityStore,
    private val metadataBackfill: com.yomira.app.metadata.MetadataBackfillScheduler,
) : SettingsMaintenanceServices {
    override val backfill = metadataBackfill.work.map { work ->
        val info = work.firstOrNull { !it.state.isFinished } ?: work.lastOrNull()
        val data = if (info?.state?.isFinished == true) info.outputData else info?.progress
        com.yomira.feature.settings.MetadataBackfillState(
            active = info?.state?.isFinished == false,
            done = data?.getInt("done", 0) ?: 0,
            total = data?.getInt("total", 0) ?: 0,
            failures = data?.getInt("failures", 0) ?: 0,
        )
    }
    override suspend fun startBackfill() = withContext(Dispatchers.IO) { metadataBackfill.start() }
    override fun cancelBackfill() { metadataBackfill.cancel() }
    override val lastSyncedAt: Flow<Long?> = flow {
        val deviceId = withContext(Dispatchers.IO) { identity.deviceId() }
        emitAll(syncDao.observeByDeviceId(deviceId).map { it?.lastSyncedAt?.time })
    }

    override suspend fun cacheSize(): Long = cache.getCacheSizeBytes()
    override suspend fun clearCache() { cache.clearAllCache().getOrThrow() }
    override suspend fun sync(): SyncResult = syncManager.sync()
}
