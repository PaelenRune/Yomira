package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yomira.core.model.ReadingMode

/**
 * One flushed slice of reading activity.
 *
 * The journey layer deliberately stores **local-only** aggregates instead of raw
 * page URLs, image bytes or chapter text: nothing here can reconstruct what the
 * user read, only how much effort it took. Rows are small (one per completed
 * chapter or per periodic flush), so a year of heavy reading stays in the low
 * thousands of rows and never needs an upload.
 *
 * [pageCount] and [chapterCount] are raw counters. [weightedPages] is the fair
 * comparison unit: a 100-page western album carries more weight than a 10-page
 * manga chapter. [xp] is already deduplicated by [ReadChapterEntity], so a
 * re-opened chapter can never award the same experience twice.
 */
@Entity(
    tableName = "reading_activity",
    indices = [
        Index("dayEpoch"),
        Index("occurredAt"),
        Index("seriesId"),
        Index("genre"),
    ],
)
data class ReadingActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val seriesId: String,
    val seriesTitle: String,
    /** Primary non-format genre, used by the "favourite genre" and "Genre Explorer" badges. */
    val genre: String,
    val readingMode: ReadingMode,
    /** Local wall-clock instant of the flush, epoch milliseconds. */
    val occurredAt: Long,
    /** Local date key (`LocalDate.toEpochDay()`), indexed for the weekly bars. */
    val dayEpoch: Long,
    val hourOfDay: Int,
    val pageCount: Int,
    val chapterCount: Int,
    val activeMillis: Long,
    /** Pages adjusted by the format multiplier; 20 weighted pages = one standard chapter. */
    val weightedPages: Double,
    val xp: Int,
    /** 1 only when this slice completed a series for the first time. */
    val completedSeries: Int = 0,
    val isWestern: Boolean = false,
    val isNight: Boolean = false,
    val isWeekend: Boolean = false,
)
