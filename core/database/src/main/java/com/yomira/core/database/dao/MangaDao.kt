package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yomira.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("UPDATE manga SET readingMode = :mode WHERE id = :mangaId")
    suspend fun updateReadingMode(mangaId: String, mode: com.yomira.core.model.ReadingMode)

    @Query("SELECT manga.* FROM manga LEFT JOIN chapters ON manga.id = chapters.mangaId GROUP BY manga.id ORDER BY MAX(chapters.releaseDate) DESC, manga.id")
    fun observeRecentlyUpdated(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga ORDER BY title COLLATE NOCASE, id")
    fun observeAll(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun observeById(id: String): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun getById(id: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId ORDER BY title COLLATE NOCASE, id")
    fun observeBySource(sourceId: String): Flow<List<MangaEntity>>

    // Unlike INSERT OR REPLACE, UPSERT does not delete the parent and cascade to chapters.
    @Upsert
    suspend fun upsert(manga: MangaEntity)

    @Upsert
    suspend fun upsertAll(manga: List<MangaEntity>)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun deleteById(id: String)
}
