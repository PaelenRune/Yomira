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
    @Query("SELECT * FROM reader_progress ORDER BY chapterId")
    suspend fun getReaderProgress(): List<com.yomira.core.database.entity.ReaderProgressEntity>

    @Query("SELECT * FROM reader_progress ORDER BY chapterId")
    fun observeReaderProgress(): kotlinx.coroutines.flow.Flow<List<com.yomira.core.database.entity.ReaderProgressEntity>>

    @Query("SELECT * FROM read_chapters ORDER BY mangaId, chapterId")
    suspend fun getReadChapters(): List<com.yomira.core.database.entity.ReadChapterEntity>

    @Query("SELECT * FROM read_chapters ORDER BY mangaId, chapterId")
    fun observeReadChapters(): kotlinx.coroutines.flow.Flow<List<com.yomira.core.database.entity.ReadChapterEntity>>
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
