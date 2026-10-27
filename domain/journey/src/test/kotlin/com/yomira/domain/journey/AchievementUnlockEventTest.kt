package com.yomira.domain.journey

import com.yomira.core.database.dao.*
import com.yomira.core.database.entity.JourneyProfileEntity
import com.yomira.core.database.entity.ReadingActivityEntity
import com.yomira.core.database.entity.UnlockedBadgeEntity
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementUnlockEventTest {
    private val clock = object : JourneyClock {
        override val zone = ZoneOffset.UTC
        override fun nowMillis() = 1_798_502_400_000L
    }

    @Test
    fun `new unlock carries both languages and pays XP only once`() = runTest {
        val badges = BadgeDao()
        val profile = ProfileDao()
        val repository = JourneyRepository(ActivityDao(1), profile, badges, clock)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.unlockEvents.collect { events += it }
        }

        repository.refreshAchievements()
        repository.refreshAchievements()

        val definition = Badges.ALL.first { it.id == Badges.FIRST_STEP }
        assertEquals(listOf(AchievementUnlockEvent(
            badgeId = definition.id,
            tier = BadgeTier.BRONZE,
            titleTr = definition.titleTr,
            titleEn = definition.titleEn,
            emoji = definition.emoji,
            xpBonus = BadgeTier.BRONZE.xp,
            descriptionTr = definition.descriptionTr,
            descriptionEn = definition.descriptionEn,
        )), events)
        assertEquals(BadgeTier.BRONZE.xp, profile.profile?.bonusXp)
    }

    @Test
    fun `simultaneous tiers each emit one event in tier order`() = runTest {
        val profile = ProfileDao()
        val repository = JourneyRepository(ActivityDao(50), profile, BadgeDao(), clock)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.unlockEvents.collect { events += it }
        }

        repository.refreshAchievements()

        assertEquals(listOf(BadgeTier.BRONZE, BadgeTier.SILVER, BadgeTier.GOLD), events.map { it.tier })
        assertTrue(events.all { it.badgeId == Badges.FIRST_STEP })
        assertEquals(events.sumOf { it.xpBonus }, profile.profile?.bonusXp)
    }

    @Test
    fun `ignored insert emits nothing and pays no XP`() = runTest {
        val profile = ProfileDao()
        val repository = JourneyRepository(ActivityDao(1), profile, BadgeDao(rejectInserts = true), clock)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.unlockEvents.collect { events += it }
        }

        repository.refreshAchievements()

        assertTrue(events.isEmpty())
        assertEquals(0, profile.profile?.bonusXp)
    }

    @Test
    fun `opening app after an unlock does not replay celebrations`() = runTest {
        val repository = JourneyRepository(ActivityDao(1), ProfileDao(), BadgeDao(), clock)
        repository.refreshAchievements()
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.unlockEvents.collect { events += it }
        }
        repository.refreshAchievements()
        assertTrue(events.isEmpty())
        assertTrue(repository.unlockEvents.replayCache.isEmpty())
    }

    private class BadgeDao(private val rejectInserts: Boolean = false) : UnlockedBadgeDao {
        private val rows = mutableListOf<UnlockedBadgeEntity>()
        override fun observeAll() = flowOf(rows.toList())
        override suspend fun all() = rows.toList()
        override suspend fun between(fromMillis: Long, toMillis: Long) =
            rows.filter { it.unlockedAt in fromMillis until toMillis }
        override suspend fun insert(badge: UnlockedBadgeEntity): Long {
            if (rejectInserts || rows.any { it.badgeId == badge.badgeId && it.tier == badge.tier }) return -1L
            rows += badge
            return rows.size.toLong()
        }
    }

    private class ProfileDao : JourneyProfileDao {
        var profile: JourneyProfileEntity? = null
        override fun observe() = flowOf(profile)
        override suspend fun get() = profile
        override suspend fun upsert(profile: JourneyProfileEntity) { this.profile = profile }
    }

    private class ActivityDao(units: Int) : ReadingActivityDao {
        private val totals = ReadingTotals(weightedPages = units * ReadingWeight.STANDARD_PAGES_PER_CHAPTER)
        override suspend fun totals() = totals
        override fun observeTotals() = flowOf(totals)
        override suspend fun totalsBetween(fromMillis: Long, toMillis: Long) = totals
        override suspend fun dailyTotals() = emptyList<DailyReadingTotal>()
        override fun observeDailyTotals() = flowOf(emptyList<DailyReadingTotal>())
        override suspend fun dailyTotalsBetween(fromDay: Long, toDay: Long) = emptyList<DailyReadingTotal>()
        override suspend fun favoriteGenre() = emptyList<GenreReadingTotal>()
        override fun observeFavoriteGenre() = flowOf(emptyList<GenreReadingTotal>())
        override suspend fun favoriteGenreBetween(fromMillis: Long, toMillis: Long) = emptyList<GenreReadingTotal>()
        override suspend fun topSeriesBetween(fromMillis: Long, toMillis: Long) = emptyList<SeriesReadingTotal>()
        override suspend fun genreBreakdownBetween(fromMillis: Long, toMillis: Long) = emptyList<GenreReadingTotal>()
        override fun observeAll() = flowOf(emptyList<ReadingActivityEntity>())
        override suspend fun count() = 0
        override suspend fun insert(activity: ReadingActivityEntity): Long = error("Not used by achievement refresh")
        override suspend fun insertAll(activities: List<ReadingActivityEntity>) = error("Not used by achievement refresh")
    }
}
