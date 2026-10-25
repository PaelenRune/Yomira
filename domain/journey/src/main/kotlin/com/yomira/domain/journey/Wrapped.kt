package com.yomira.domain.journey

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class WrappedPeriod { MONTHLY, YEARLY }

/**
 * A closed instant/day range for Wrapped. Months are calendar months, years are
 * calendar years; both are resolved in the user's own time zone so a reading
 * session at 23:59 on the last day belongs to the month the user experienced.
 */
data class WrappedRange(
    val period: WrappedPeriod,
    val reference: LocalDate,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    val startDay: Long,
    val endDayInclusive: Long,
)

fun WrappedPeriod.range(reference: LocalDate, zone: ZoneId): WrappedRange {
    val start = when (this) {
        WrappedPeriod.MONTHLY -> reference.withDayOfMonth(1)
        WrappedPeriod.YEARLY -> reference.withDayOfYear(1)
    }
    val end = when (this) {
        WrappedPeriod.MONTHLY -> start.plusMonths(1)
        WrappedPeriod.YEARLY -> start.plusYears(1)
    }
    return WrappedRange(
        period = this,
        reference = reference,
        startDate = start,
        endDateExclusive = end,
        startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = end.atStartOfDay(zone).toInstant().toEpochMilli(),
        startDay = start.toEpochDay(),
        endDayInclusive = end.minusDays(1).toEpochDay(),
    )
}

data class WrappedSummary(
    val period: WrappedPeriod,
    val reference: LocalDate,
    val chapters: Int,
    val pages: Int,
    val readingUnits: Int,
    val activeMillis: Long,
    val completedSeries: Int,
    val activeDays: Int,
    val topGenre: String?,
    val topSeries: String?,
    val growthPercent: Int?,
    val level: LevelState,
    val badgesEarned: List<BadgeTier>,
    val genreBreakdown: List<GenreSlice> = emptyList(),
) {
    val isEmpty: Boolean get() = chapters == 0 && pages == 0 && activeMillis == 0L
}

data class GenreSlice(val genre: String, val chapters: Int, val fraction: Float)

/**
 * Growth is intentionally null when the previous period had no reading: "+∞%"
 * is noise, while a first month deserves its own "ilk ayın" story in the UI.
 */
fun growthPercent(currentChapters: Int, previousChapters: Int): Int? {
    if (previousChapters <= 0) return null
    return ((currentChapters - previousChapters) * 100.0 / previousChapters).roundToInt()
}
