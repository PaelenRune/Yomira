package com.yomira.domain.journey

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappedTest {
    private val zone: ZoneId = ZoneOffset.UTC

    @Test fun `monthly range covers the calendar month`() {
        val range = WrappedPeriod.MONTHLY.range(LocalDate.of(2026, 10, 25), zone)
        assertEquals(LocalDate.of(2026, 10, 1), range.startDate)
        assertEquals(LocalDate.of(2026, 11, 1), range.endDateExclusive)
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), range.endDayInclusive)
        assertTrue(range.startMillis < range.endMillis)
    }

    @Test fun `yearly range covers the calendar year`() {
        val range = WrappedPeriod.YEARLY.range(LocalDate.of(2026, 10, 25), zone)
        assertEquals(LocalDate.of(2026, 1, 1), range.startDate)
        assertEquals(LocalDate.of(2027, 1, 1), range.endDateExclusive)
        assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), range.endDayInclusive)
    }

    @Test fun `growth is null for a first period and rounded down otherwise`() {
        assertNull(growthPercent(120, 0))
        assertNull(growthPercent(0, 0))
        assertEquals(31, growthPercent(186, 142))
        assertEquals(-50, growthPercent(50, 100))
    }

    @Test fun `empty summary is detectable`() {
        val summary = WrappedSummary(
            period = WrappedPeriod.MONTHLY,
            reference = LocalDate.of(2026, 10, 1),
            chapters = 0,
            pages = 0,
            readingUnits = 0,
            activeMillis = 0,
            completedSeries = 0,
            activeDays = 0,
            topGenre = null,
            topSeries = null,
            growthPercent = null,
            level = LevelProgression.stateForXp(0),
            badgesEarned = emptyList(),
        )
        assertTrue(summary.isEmpty)
    }

    @Test fun `genre slices stay between zero and one`() {
        val slice = GenreSlice("Action", 12, (12f / 20f))
        assertTrue(slice.fraction in 0f..1f)
    }
}
