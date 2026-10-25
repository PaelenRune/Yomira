package com.yomira.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexibleStreakTest {
    private val today = 20_000L

    private fun daysAgo(vararg days: Long): Set<Long> = days.map { today - it }.toSet()

    @Test fun `seven consecutive days is a seven day streak`() {
        val streak = FlexibleStreak.evaluate(daysAgo(0, 1, 2, 3, 4, 5, 6), today)
        assertEquals(7, streak.current)
    }

    @Test fun `one missed day inside a week keeps the flame alive`() {
        val streak = FlexibleStreak.evaluate(daysAgo(0, 1, 3, 4, 5, 6), today)
        assertEquals(6, streak.current)
    }

    @Test fun `two consecutive missed days break the streak`() {
        val streak = FlexibleStreak.evaluate(daysAgo(0, 4, 5, 6), today)
        assertEquals(1, streak.current)
    }

    @Test fun `today is never punished before it ends`() {
        val streak = FlexibleStreak.evaluate(daysAgo(1, 2, 3), today)
        assertEquals(3, streak.current)
    }

    @Test fun `no reading yesterday or today resets to zero`() {
        assertEquals(0, FlexibleStreak.evaluate(daysAgo(2, 3, 4), today).current)
        assertEquals(0, FlexibleStreak.evaluate(emptySet(), today).current)
    }

    @Test fun `grace day is reported as available until used`() {
        assertTrue(FlexibleStreak.evaluate(daysAgo(0, 1, 2), today).graceAvailable)
        assertFalse(FlexibleStreak.evaluate(daysAgo(0, 2, 3), today).graceAvailable)
    }

    @Test fun `longest streak survives a broken run`() {
        val active = daysAgo(10, 11, 12, 13, 14, 0, 1)
        assertEquals(5, FlexibleStreak.evaluate(active, today).longest)
    }
}
