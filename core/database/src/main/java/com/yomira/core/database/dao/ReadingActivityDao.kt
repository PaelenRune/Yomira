package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yomira.core.database.entity.ReadingActivityEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local-first reading ledger.
 *
 * Every "weighted" figure is computed at write time by the journey layer, so the
 * SQL here stays index-friendly: weekly bars group by `dayEpoch`, Wrapped reads
 * an instant range, and badge metrics use the all-time aggregate. No row ever
 * leaves the device.
 */
@Dao
interface ReadingActivityDao {
    @Insert
    suspend fun insert(activity: ReadingActivityEntity): Long

    @Insert
    suspend fun insertAll(activities: List<ReadingActivityEntity>)

    @Query("SELECT * FROM reading_activity ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<ReadingActivityEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(weightedPages), 0.0) AS weightedPages,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(activeMillis), 0) AS activeMillis,
               COALESCE(SUM(xp), 0) AS xp,
               COALESCE(SUM(completedSeries), 0) AS completedSeries,
               COUNT(DISTINCT dayEpoch) AS activeDays,
               COALESCE(SUM(CASE WHEN isNight = 1 THEN chapterCount ELSE 0 END), 0) AS nightChapters,
               COALESCE(SUM(CASE WHEN isWeekend = 1 THEN chapterCount ELSE 0 END), 0) AS weekendChapters,
               COUNT(DISTINCT genre) AS genres,
               COUNT(DISTINCT CASE WHEN isWestern = 1 THEN seriesId END) AS westernSeries
        FROM reading_activity
        """,
    )
    fun observeTotals(): Flow<ReadingTotals>

    @Query(
        """
        SELECT COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(weightedPages), 0.0) AS weightedPages,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(activeMillis), 0) AS activeMillis,
               COALESCE(SUM(xp), 0) AS xp,
               COALESCE(SUM(completedSeries), 0) AS completedSeries,
               COUNT(DISTINCT dayEpoch) AS activeDays,
               COALESCE(SUM(CASE WHEN isNight = 1 THEN chapterCount ELSE 0 END), 0) AS nightChapters,
               COALESCE(SUM(CASE WHEN isWeekend = 1 THEN chapterCount ELSE 0 END), 0) AS weekendChapters,
               COUNT(DISTINCT genre) AS genres,
               COUNT(DISTINCT CASE WHEN isWestern = 1 THEN seriesId END) AS westernSeries
        FROM reading_activity
        """,
    )
    suspend fun totals(): ReadingTotals

    @Query(
        """
        SELECT COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(weightedPages), 0.0) AS weightedPages,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(activeMillis), 0) AS activeMillis,
               COALESCE(SUM(xp), 0) AS xp,
               COALESCE(SUM(completedSeries), 0) AS completedSeries,
               COUNT(DISTINCT dayEpoch) AS activeDays,
               COALESCE(SUM(CASE WHEN isNight = 1 THEN chapterCount ELSE 0 END), 0) AS nightChapters,
               COALESCE(SUM(CASE WHEN isWeekend = 1 THEN chapterCount ELSE 0 END), 0) AS weekendChapters,
               COUNT(DISTINCT genre) AS genres,
               COUNT(DISTINCT CASE WHEN isWestern = 1 THEN seriesId END) AS westernSeries
        FROM reading_activity
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
        """,
    )
    suspend fun totalsBetween(fromMillis: Long, toMillis: Long): ReadingTotals

    @Query(
        """
        SELECT dayEpoch,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(activeMillis), 0) AS activeMillis,
               COALESCE(SUM(weightedPages), 0.0) AS weightedPages
        FROM reading_activity
        GROUP BY dayEpoch
        ORDER BY dayEpoch
        """,
    )
    fun observeDailyTotals(): Flow<List<DailyReadingTotal>>

    @Query(
        """
        SELECT dayEpoch,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(activeMillis), 0) AS activeMillis,
               COALESCE(SUM(weightedPages), 0.0) AS weightedPages
        FROM reading_activity
        GROUP BY dayEpoch
        ORDER BY dayEpoch
        """,
    )
    suspend fun dailyTotals(): List<DailyReadingTotal>

    @Query(
        """
        SELECT dayEpoch,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(activeMillis), 0) AS activeMillis,
               COALESCE(SUM(weightedPages), 0.0) AS weightedPages
        FROM reading_activity
        WHERE dayEpoch BETWEEN :fromDay AND :toDay
        GROUP BY dayEpoch
        ORDER BY dayEpoch
        """,
    )
    suspend fun dailyTotalsBetween(fromDay: Long, toDay: Long): List<DailyReadingTotal>

    @Query(
        """
        SELECT genre,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(activeMillis), 0) AS activeMillis
        FROM reading_activity
        WHERE chapterCount > 0
        GROUP BY genre
        ORDER BY SUM(chapterCount) DESC, SUM(activeMillis) DESC, genre
        LIMIT 1
        """,
    )
    fun observeFavoriteGenre(): Flow<List<GenreReadingTotal>>

    @Query(
        """
        SELECT genre,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(activeMillis), 0) AS activeMillis
        FROM reading_activity
        WHERE chapterCount > 0
        GROUP BY genre
        ORDER BY SUM(chapterCount) DESC, SUM(activeMillis) DESC, genre
        LIMIT 1
        """,
    )
    suspend fun favoriteGenre(): List<GenreReadingTotal>

    @Query(
        """
        SELECT genre,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(activeMillis), 0) AS activeMillis
        FROM reading_activity
        WHERE chapterCount > 0 AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        GROUP BY genre
        ORDER BY SUM(chapterCount) DESC, SUM(activeMillis) DESC, genre
        LIMIT 1
        """,
    )
    suspend fun favoriteGenreBetween(fromMillis: Long, toMillis: Long): List<GenreReadingTotal>

    @Query(
        """
        SELECT seriesId, seriesTitle,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(activeMillis), 0) AS activeMillis
        FROM reading_activity
        WHERE occurredAt >= :fromMillis AND occurredAt < :toMillis
        GROUP BY seriesId, seriesTitle
        ORDER BY SUM(chapterCount) DESC, SUM(activeMillis) DESC, seriesTitle
        LIMIT 1
        """,
    )
    suspend fun topSeriesBetween(fromMillis: Long, toMillis: Long): List<SeriesReadingTotal>

    @Query(
        """
        SELECT genre,
               COALESCE(SUM(chapterCount), 0) AS chapters,
               COALESCE(SUM(pageCount), 0) AS pages,
               COALESCE(SUM(activeMillis), 0) AS activeMillis
        FROM reading_activity
        WHERE chapterCount > 0 AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        GROUP BY genre
        ORDER BY SUM(chapterCount) DESC, SUM(activeMillis) DESC, genre
        """,
    )
    suspend fun genreBreakdownBetween(fromMillis: Long, toMillis: Long): List<GenreReadingTotal>

    @Query("SELECT COUNT(*) FROM reading_activity")
    suspend fun count(): Int
}
