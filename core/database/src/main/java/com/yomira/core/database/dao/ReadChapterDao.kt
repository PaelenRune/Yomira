package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yomira.core.database.entity.ReadChapterEntity

/**
 * Deduplication ledger for XP. The first completion of a chapter is the only
 * one that can pay the full bundle; every later completion increments
 * [ReadChapterEntity.completionCount] and yields the reduced re-read amount.
 */
@Dao
interface ReadChapterDao {
    @Query("SELECT * FROM read_chapters WHERE mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun get(mangaId: String, chapterId: String): ReadChapterEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: ReadChapterEntity): Long

    @Query(
        """
        UPDATE read_chapters
        SET lastReadAt = :now, completedAt = :now, completionCount = completionCount + 1
        WHERE mangaId = :mangaId AND chapterId = :chapterId
        """,
    )
    suspend fun touchCompletion(mangaId: String, chapterId: String, now: Long)

    /** @return true when this is the first completion of the chapter. */
    @Transaction
    suspend fun recordCompletion(mangaId: String, chapterId: String, now: Long): Boolean {
        val existing = get(mangaId, chapterId)
        if (existing == null) {
            insert(
                ReadChapterEntity(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    firstReadAt = now,
                    lastReadAt = now,
                    completionCount = 1,
                    fullXpAwarded = true,
                    completedAt = now,
                ),
            )
            return true
        }
        touchCompletion(mangaId, chapterId, now)
        return false
    }

    @Query("SELECT COUNT(*) FROM read_chapters")
    fun observeCompletedCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM read_chapters")
    suspend fun completedCount(): Int

    @Query("SELECT COUNT(*) FROM read_chapters WHERE mangaId = :mangaId")
    suspend fun completedCountForManga(mangaId: String): Int

    @Query("SELECT COUNT(DISTINCT mangaId) FROM read_chapters")
    suspend fun distinctSeriesCount(): Int
}
