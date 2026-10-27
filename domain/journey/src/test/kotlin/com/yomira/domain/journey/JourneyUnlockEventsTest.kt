package com.yomira.domain.journey

import com.yomira.core.database.dao.DailyReadingTotal
import com.yomira.core.database.dao.GenreReadingTotal
import com.yomira.core.database.dao.JourneyProfileDao
import com.yomira.core.database.dao.ReadingActivityDao
import com.yomira.core.database.dao.ReadingTotals
import com.yomira.core.database.dao.SeriesReadingTotal
import com.yomira.core.database.dao.UnlockedBadgeDao
import com.yomira.core.database.entity.JourneyProfileEntity
import com.yomira.core.database.entity.ReadingActivityEntity
import com.yomira.core.database.entity.UnlockedBadgeEntity
import java.time.ZoneId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JourneyUnlockEventsTest {
    @Test
    fun `new tier emits badge details and awards bonus only once`() = runTest {
        val fixture = Fixture(units = 1)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.repository.unlockEvents.collect { events += it } }

        fixture.repository.refreshAchievements()
        fixture.repository.refreshAchievements()
        runCurrent()

        val badge = Badges.byId(Badges.FIRST_STEP)!!
        assertEquals(listOf(AchievementUnlockEvent(
            badgeId = badge.id,
            tier = BadgeTier.BRONZE,
            titleTr = badge.titleTr,
            titleEn = badge.titleEn,
            emoji = badge.emoji,
            xpBonus = 50,
            descriptionTr = badge.descriptionTr,
            descriptionEn = badge.descriptionEn,
        )), events)
        assertEquals(50, fixture.profile.value?.bonusXp)
    }

    @Test
    fun `multiple newly earned tiers emit in order without losing unlocks`() = runTest {
        val fixture = Fixture(units = 50)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.repository.unlockEvents.collect { events += it } }

        fixture.repository.refreshAchievements()
        runCurrent()

        assertEquals(listOf(BadgeTier.BRONZE, BadgeTier.SILVER, BadgeTier.GOLD), events.map { it.tier })
        assertEquals(475, fixture.profile.value?.bonusXp)
    }

    @Test
    fun `ignored insert never celebrates or pays bonus`() = runTest {
        val fixture = Fixture(units = 1, ignoreInsert = true)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.repository.unlockEvents.collect { events += it } }

        fixture.repository.refreshAchievements()
        runCurrent()

        assertTrue(events.isEmpty())
        assertEquals(0, fixture.profile.value?.bonusXp)
    }

    @Test
    fun `unlocks are not replayed to a later subscriber`() = runTest {
        val fixture = Fixture(units = 1)
        fixture.repository.refreshAchievements()
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.repository.unlockEvents.collect { events += it } }
        fixture.repository.refreshAchievements()
        runCurrent()

        assertTrue(events.isEmpty())
        assertTrue(fixture.repository.unlockEvents.replayCache.isEmpty())
    }

    @Test
    fun `concurrent refresh calls only emit one event per tier`() = runTest {
        val fixture = Fixture(units = 10)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.repository.unlockEvents.collect { events += it } }

        List(8) { launch { fixture.repository.refreshAchievements() } }.forEach { it.join() }
        runCurrent()

        assertEquals(listOf(BadgeTier.BRONZE, BadgeTier.SILVER), events.map { it.tier })
        assertEquals(175, fixture.profile.value?.bonusXp)
    }

    @Test
    fun `below threshold produces no event`() = runTest {
        val fixture = Fixture(units = 0)
        val events = mutableListOf<AchievementUnlockEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { fixture.repository.unlockEvents.collect { events += it } }

        fixture.repository.refreshAchievements()
        runCurrent()

        assertTrue(events.isEmpty())
        assertEquals(0, fixture.profile.value?.bonusXp)
    }

    private class Fixture(units: Int, ignoreInsert: Boolean = false) {
        val profile = MemoryProfileDao()
        val repository = JourneyRepository(
            FixedActivityDao(ReadingTotals(weightedPages = units * ReadingWeight.STANDARD_PAGES_PER_CHAPTER)),
            profile,
            MemoryBadgeDao(ignoreInsert),
            object : JourneyClock {
                override val zone = ZoneId.of("UTC")
                override fun nowMillis() = 1_700_000_000_000L
            },
        )
    }

    private class MemoryProfileDao : JourneyProfileDao {
        var value: JourneyProfileEntity? = null
        override fun observe() = flowOf(value)
        override suspend fun get() = value
        override suspend fun upsert(profile: JourneyProfileEntity) { value = profile }
    }

    private class MemoryBadgeDao(private val ignoreInsert: Boolean) : UnlockedBadgeDao {
        private val rows = mutableListOf<UnlockedBadgeEntity>()
        override fun observeAll() = flowOf(rows.toList())
        override suspend fun all() = rows.toList()
        override suspend fun between(fromMillis: Long, toMillis: Long) = rows.filter { it.unlockedAt in fromMillis until toMillis }
        override suspend fun insert(badge: UnlockedBadgeEntity): Long {
            if (ignoreInsert || rows.any { it.badgeId == badge.badgeId && it.tier == badge.tier }) return -1L
            rows += badge
            return rows.size.toLong()
        }
    }

    private class FixedActivityDao(private val total: ReadingTotals) : ReadingActivityDao {
        override suspend fun totals() = total
        override suspend fun dailyTotals() = emptyList<DailyReadingTotal>()
        override suspend fun favoriteGenre() = emptyList<GenreReadingTotal>()
        override fun observeTotals() = flowOf(total)
        override fun observeDailyTotals() = flowOf(emptyList<DailyReadingTotal>())
        override fun observeFavoriteGenre() = flowOf(emptyList<GenreReadingTotal>())
        override fun observeAll(): Flow<List<ReadingActivityEntity>> = flowOf(emptyList())
        override suspend fun insert(activity: ReadingActivityEntity): Long = error("Not used")
        override suspend fun insertAll(activities: List<ReadingActivityEntity>) = error("Not used")
        override suspend fun totalsBetween(fromMillis: Long, toMillis: Long) = total
        override suspend fun dailyTotalsBetween(fromDay: Long, toDay: Long) = emptyList<DailyReadingTotal>()
        override suspend fun favoriteGenreBetween(fromMillis: Long, toMillis: Long) = emptyList<GenreReadingTotal>()
        override suspend fun topSeriesBetween(fromMillis: Long, toMillis: Long) = emptyList<SeriesReadingTotal>()
        override suspend fun genreBreakdownBetween(fromMillis: Long, toMillis: Long) = emptyList<GenreReadingTotal>()
        override suspend fun count() = 0
    }
}
