package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yomira.core.database.entity.LibraryEntryEntity
import com.yomira.core.database.entity.ReadingProgressEntity
import com.yomira.core.database.entity.SyncSettingsEntity

/** Read/write operations used by the sync orchestrator. Writes are applied in a transaction. */
@Dao
interface SyncDataDao {
    @Query("SELECT * FROM library_entries ORDER BY mangaId")
    suspend fun getAllLibraryEntries(): List<LibraryEntryEntity>

    @Upsert
    suspend fun upsertLibraryEntries(entries: List<LibraryEntryEntity>)

    @Query("SELECT * FROM reading_progress ORDER BY mangaId")
    suspend fun getAllReadingProgress(): List<ReadingProgressEntity>

    @Upsert
    suspend fun upsertReadingProgress(entries: List<ReadingProgressEntity>)

    @Query("SELECT * FROM sync_settings WHERE id = 1")
    suspend fun getSettings(): SyncSettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: SyncSettingsEntity)
}
