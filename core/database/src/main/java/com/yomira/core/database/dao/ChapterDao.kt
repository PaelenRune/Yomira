package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.yomira.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM reader_progress WHERE chapterId = :chapterId")
    suspend fun getProgress(chapterId: String): com.yomira.core.database.entity.ReaderProgressEntity?

    @Upsert
    suspend fun saveProgress(progress: com.yomira.core.database.entity.ReaderProgressEntity)

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY chapterNumber, id")
    fun observeByMangaId(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY chapterNumber, id")
    suspend fun getByMangaId(mangaId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: String): ChapterEntity?

    @Upsert
    suspend fun upsert(chapter: ChapterEntity)

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: String)

    /** Replace a fetched chapter index atomically, including removal of stale references. */
    @Transaction
    suspend fun replaceForManga(mangaId: String, chapters: List<ChapterEntity>) {
        require(chapters.all { it.mangaId == mangaId }) { "Chapter belongs to another manga" }
        require(chapters.map { it.id }.distinct().size == chapters.size) { "Duplicate chapter IDs" }
        deleteByMangaId(mangaId)
        upsertAll(chapters)
    }
}
