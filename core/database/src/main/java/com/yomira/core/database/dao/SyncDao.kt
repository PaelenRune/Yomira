package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.core.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM reader_progress WHERE mangaId = :mangaId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestReaderProgress(mangaId: String): com.yomira.core.database.entity.ReaderProgressEntity?

    /** Newest open page across the whole library; drives the Home resume capsule. */
    @Query("SELECT * FROM reader_progress ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatestReaderProgress(): Flow<ReaderProgressEntity?>

    /** Every series with at least one opened page; reading counts as library membership for search. */
    @Query("SELECT DISTINCT mangaId FROM reader_progress")
    fun observeReadingMangaIds(): Flow<List<String>>

    @Query("SELECT * FROM reader_progress WHERE mangaId = :mangaId ORDER BY updatedAt DESC")
    fun observeReaderProgress(mangaId: String): Flow<List<ReaderProgressEntity>>

    @Query("DELETE FROM reader_progress WHERE mangaId = :mangaId")
    suspend fun deleteReaderProgress(mangaId: String)

    @Query("DELETE FROM reading_progress WHERE mangaId = :mangaId")
    suspend fun deleteReadingProgress(mangaId: String)

    @Transaction
    suspend fun resetProgress(mangaId: String) {
        deleteReaderProgress(mangaId)
        deleteReadingProgress(mangaId)
    }

    /** Local dirty revision, separate from the server's lastSyncedAt watermark. */
    @Transaction
    suspend fun markReaderDirty(deviceId: String) {
        ensureReaderRevision(deviceId)
        incrementReaderRevision(deviceId)
    }

    @Query("INSERT OR IGNORE INTO sync_state(deviceId, lastSyncedAt, version) VALUES (:deviceId, NULL, 0)")
    suspend fun ensureReaderRevision(deviceId: String)

    @Query("UPDATE sync_state SET version = version + 1 WHERE deviceId = :deviceId")
    suspend fun incrementReaderRevision(deviceId: String)

    @Query("SELECT * FROM sync_state WHERE deviceId = :deviceId")
    fun observeByDeviceId(deviceId: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE deviceId = :deviceId")
    suspend fun getByDeviceId(deviceId: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    /** Compare-and-set prevents concurrent sync workers from overwriting newer state. */
    @Query(
        "UPDATE sync_state SET version = :newVersion, lastSyncedAt = :syncedAt " +
            "WHERE deviceId = :deviceId AND version = :expectedVersion AND :newVersion > version",
    )
    suspend fun advanceVersion(
        deviceId: String,
        expectedVersion: Long,
        newVersion: Long,
        syncedAt: java.util.Date,
    ): Int

    @Query("DELETE FROM sync_state WHERE deviceId = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String)
}
