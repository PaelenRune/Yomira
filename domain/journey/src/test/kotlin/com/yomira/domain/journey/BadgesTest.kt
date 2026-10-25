package com.yomira.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgesTest {
    private fun stats(
        units: Int = 0,
        streak: Int = 0,
        genres: Int = 0,
        western: Int = 0,
        night: Int = 0,
        weekend: Int = 0,
    ) = JourneyStats(
        readingUnits = units,
        currentStreak = streak,
        distinctGenres = genres,
        westernSeries = western,
        nightChapters = night,
        weekendChapters = weekend,
    )

    @Test fun `four categories and eight badges are exposed`() {
        assertEquals(4, BadgeCategory.entries.size)
        assertEquals(8, Badges.ALL.size)
        Badges.ALL.forEach { badge ->
            assertEquals(4, badge.thresholds.size)
            assertTrue(badge.thresholds.zipWithNext().all { (a, b) -> b > a })
        }
        assertEquals(8, Badges.ALL.map { it.id }.distinct().size)
    }

    @Test fun `first step unlocks bronze at one reading unit`() {
        val badge = Badges.byId(Badges.FIRST_STEP)!!
        val locked = Badges.progress(badge, stats(units = 0), emptySet())
        assertFalse(locked.isUnlocked)
        assertNull(locked.currentTier)

        val bronze = Badges.progress(badge, stats(units = 1), setOf(BadgeTier.BRONZE))
        assertTrue(bronze.isUnlocked)
        assertEquals(BadgeTier.BRONZE, bronze.currentTier)
        assertEquals(10, bronze.nextTarget)
    }

    @Test fun `hundreds club uses reading units not raw chapters`() {
        val badge = Badges.byId(Badges.HUNDREDS_CLUB)!!
        val progress = Badges.progress(badge, stats(units = 250), setOf(BadgeTier.BRONZE, BadgeTier.SILVER))
        assertEquals(BadgeTier.SILVER, progress.currentTier)
        assertEquals(500, progress.nextTarget)
    }

    @Test fun `legendary reader caps at platinum`() {
        val badge = Badges.byId(Badges.LEGENDARY_READER)!!
        val progress = Badges.progress(badge, stats(units = 12_000), BadgeTier.entries.toSet())
        assertTrue(progress.isFullyUnlocked)
        assertNull(progress.nextTarget)
        assertEquals(1f, progress.progress, 0f)
    }

    @Test fun `streak badge tracks the flexible current streak`() {
        val badge = Badges.byId(Badges.FLAME_STREAK)!!
        assertEquals(BadgeTier.BRONZE, Badges.progress(badge, stats(streak = 7), setOf(BadgeTier.BRONZE)).currentTier)
        assertEquals(BadgeTier.GOLD, Badges.progress(badge, stats(streak = 30), emptySet()).currentTier)
    }

    @Test fun `night owl and weekend marathoner read their own counters`() {
        val night = Badges.byId(Badges.NIGHT_OWL)!!
        val weekend = Badges.byId(Badges.WEEKEND_MARATHONER)!!
        assertEquals(BadgeTier.BRONZE, Badges.progress(night, stats(night = 50), emptySet()).currentTier)
        assertEquals(BadgeTier.SILVER, Badges.progress(weekend, stats(weekend = 100), emptySet()).currentTier)
    }

    @Test fun `exploration badges count genres and western series`() {
        val genre = Badges.byId(Badges.GENRE_EXPLORER)!!
        val western = Badges.byId(Badges.WESTERN_EXPLORER)!!
        assertEquals(BadgeTier.BRONZE, Badges.progress(genre, stats(genres = 5), emptySet()).currentTier)
        assertEquals(BadgeTier.BRONZE, Badges.progress(western, stats(western = 1), emptySet()).currentTier)
    }

    @Test fun `progress fraction is always bounded`() {
        Badges.ALL.forEach { badge ->
            listOf(0, 1, 50, 100, 10_000).forEach { value ->
                val progress = Badges.progress(
                    badge,
                    stats(units = value, streak = value, genres = value, western = value, night = value, weekend = value),
                    emptySet(),
                )
                assertTrue("${badge.id} at $value -> ${progress.progress}", progress.progress in 0f..1f)
                assertNotNull(progress.badge)
            }
        }
    }
}

class JourneyStatsDefaultsTest {
    @Test fun `default stats start at level one with no activity`() {
        val stats = JourneyStats()
        assertEquals(1, stats.level.level)
        assertTrue(stats.weekly.isEmpty())
        assertEquals(0, stats.unlockedBasis())
    }

    private fun JourneyStats.unlockedBasis(): Int = totalXp
}
