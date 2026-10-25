package com.yomira.core.database.dao

import androidx.room.ColumnInfo

/**
 * Aggregates returned by [ReadingActivityDao]. Kept as plain projection types so
 * the journey layer never has to load every activity row into memory.
 */
data class ReadingTotals(
    @ColumnInfo(name = "pages") val pages: Int = 0,
    @ColumnInfo(name = "weightedPages") val weightedPages: Double = 0.0,
    @ColumnInfo(name = "chapters") val chapters: Int = 0,
    @ColumnInfo(name = "activeMillis") val activeMillis: Long = 0L,
    @ColumnInfo(name = "xp") val xp: Int = 0,
    @ColumnInfo(name = "completedSeries") val completedSeries: Int = 0,
    @ColumnInfo(name = "activeDays") val activeDays: Int = 0,
    @ColumnInfo(name = "nightChapters") val nightChapters: Int = 0,
    @ColumnInfo(name = "weekendChapters") val weekendChapters: Int = 0,
    @ColumnInfo(name = "genres") val genres: Int = 0,
    @ColumnInfo(name = "westernSeries") val westernSeries: Int = 0,
)

data class DailyReadingTotal(
    @ColumnInfo(name = "dayEpoch") val dayEpoch: Long,
    @ColumnInfo(name = "pages") val pages: Int,
    @ColumnInfo(name = "chapters") val chapters: Int,
    @ColumnInfo(name = "activeMillis") val activeMillis: Long,
    @ColumnInfo(name = "weightedPages") val weightedPages: Double,
)

data class GenreReadingTotal(
    @ColumnInfo(name = "genre") val genre: String,
    @ColumnInfo(name = "chapters") val chapters: Int,
    @ColumnInfo(name = "pages") val pages: Int,
    @ColumnInfo(name = "activeMillis") val activeMillis: Long,
)

data class SeriesReadingTotal(
    @ColumnInfo(name = "seriesId") val seriesId: String,
    @ColumnInfo(name = "seriesTitle") val seriesTitle: String,
    @ColumnInfo(name = "chapters") val chapters: Int,
    @ColumnInfo(name = "pages") val pages: Int,
    @ColumnInfo(name = "activeMillis") val activeMillis: Long,
)
