package com.yomira.domain.journey

import com.yomira.core.database.dao.DailyReadingTotal
import com.yomira.core.database.dao.JourneyProfileDao
import com.yomira.core.database.dao.ReadingActivityDao
import com.yomira.core.database.dao.ReadingTotals
import com.yomira.core.database.dao.UnlockedBadgeDao
import com.yomira.core.database.entity.ReadingActivityEntity
import com.yomira.core.database.entity.UnlockedBadgeEntity
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only class that writes to the journey tables.
 *
 * All writes are local Room inserts. There is no network call, no account and
 * no upload path anywhere in this module: the reading rhythm is private.
 */
@Singleton
class JourneyRepository @Inject constructor(
    private val activityDao: ReadingActivityDao,
    private val profileDao: JourneyProfileDao,
    private val badgeDao: UnlockedBadgeDao,
    private val clock: JourneyClock,
) {
    private val awardMutex = Mutex()
    private val _unlockEvents = MutableSharedFlow<AchievementUnlockEvent>(extraBufferCapacity = 32)
    // No replay: opening the app must not celebrate previously earned badges again.
    val unlockEvents: SharedFlow<AchievementUnlockEvent> = _unlockEvents.asSharedFlow()

    fun observeDashboard(): Flow<JourneyDashboard> = combine(
        activityDao.observeTotals(),
        activityDao.observeDailyTotals(),
        profileDao.observe(),
        badgeDao.observeAll(),
        activityDao.observeFavoriteGenre(),
    ) { totals, daily, profile, badges, favorite ->
        val unlocked = unlockedMap(badges)
        val stats = buildStats(
            totals = totals,
            daily = daily,
            bonusXp = profile?.bonusXp ?: 0,
            favoriteGenre = favorite.firstOrNull()?.genre,
        )
        JourneyDashboard(
            stats = stats,
            badges = buildProgress(stats, unlocked),
            unlockedCount = unlocked.values.sumOf { it.size },
        )
    }.distinctUntilChanged()

    suspend fun refreshAchievements() = awardMutex.withLock {
        val totals = activityDao.totals()
        val daily = activityDao.dailyTotals()
        val profile = profileDao.get()
        val unlocked = unlockedMap(badgeDao.all())
        val stats = buildStats(totals, daily, profile?.bonusXp ?: 0, activityDao.favoriteGenre().firstOrNull()?.genre)
        val now = clock.nowMillis()
        var bonus = 0
        Badges.ALL.forEach { definition ->
            val current = definition.metric(stats).coerceAtLeast(0)
            definition.thresholds.forEachIndexed { index, threshold ->
                if (current < threshold) return@forEachIndexed
                val tier = BadgeTier.entries[index]
                if (unlocked[definition.id]?.contains(tier) == true) return@forEachIndexed
                val inserted = badgeDao.insert(
                    UnlockedBadgeEntity(
                        badgeId = definition.id,
                        tier = tier.name,
                        unlockedAt = now,
                    ),
                )
                if (inserted != -1L) {
                    bonus += tier.xp
                    _unlockEvents.emit(
                        AchievementUnlockEvent(
                            badgeId = definition.id,
                            tier = tier,
                            titleTr = definition.titleTr,
                            titleEn = definition.titleEn,
                            emoji = definition.emoji,
                            xpBonus = tier.xp,
                            descriptionTr = definition.descriptionTr,
                            descriptionEn = definition.descriptionEn,
                        ),
                    )
                }
            }
        }
        if (bonus > 0) profileDao.awardBonusXp(bonus, now)
        profileDao.touch(clock.todayEpochDay(), now)
    }

    suspend fun wrapped(
        period: WrappedPeriod,
        reference: java.time.LocalDate = clock.today(),
    ): WrappedSummary {
        val range = period.range(reference, clock.zone)
        val previousReference = when (period) {
            WrappedPeriod.MONTHLY -> range.startDate.minusMonths(1)
            WrappedPeriod.YEARLY -> range.startDate.minusYears(1)
        }
        val previousRange = period.range(previousReference, clock.zone)

        val current = activityDao.totalsBetween(range.startMillis, range.endMillis)
        val previous = activityDao.totalsBetween(previousRange.startMillis, previousRange.endMillis)
        val daily = activityDao.dailyTotalsBetween(range.startDay, range.endDayInclusive)
        val favorite = activityDao.favoriteGenreBetween(range.startMillis, range.endMillis).firstOrNull()
        val topSeries = activityDao.topSeriesBetween(range.startMillis, range.endMillis).firstOrNull()
        val genres = activityDao.genreBreakdownBetween(range.startMillis, range.endMillis)
        val allTime = activityDao.totals()
        val profile = profileDao.get()
        val totalXp = allTime.xp + (profile?.bonusXp ?: 0)
        val earned = badgeDao.between(range.startMillis, range.endMillis)
            .mapNotNull { runCatching { BadgeTier.valueOf(it.tier) }.getOrNull() }
            .distinct()
            .sortedBy { it.ordinal }

        return WrappedSummary(
            period = period,
            reference = reference,
            chapters = current.chapters,
            pages = current.pages,
            readingUnits = (current.weightedPages / ReadingWeight.STANDARD_PAGES_PER_CHAPTER).toInt(),
            activeMillis = current.activeMillis,
            completedSeries = current.completedSeries,
            activeDays = daily.size,
            topGenre = favorite?.genre,
            topSeries = topSeries?.seriesTitle,
            growthPercent = growthPercent(current.chapters, previous.chapters),
            level = LevelProgression.stateForXp(totalXp),
            badgesEarned = earned,
            genreBreakdown = genres.take(4).map { slice ->
                GenreSlice(
                    genre = slice.genre,
                    chapters = slice.chapters,
                    fraction = if (current.chapters <= 0) 0f
                    else (slice.chapters.toFloat() / current.chapters.toFloat()).coerceIn(0f, 1f),
                )
            },
        )
    }

    private fun buildStats(
        totals: ReadingTotals,
        daily: List<DailyReadingTotal>,
        bonusXp: Int,
        favoriteGenre: String?,
    ): JourneyStats {
        val today = clock.todayEpochDay()
        val activeDays = daily.mapTo(HashSet()) { it.dayEpoch }
        val streak = FlexibleStreak.evaluate(activeDays, today)
        val byDay = daily.associateBy { it.dayEpoch }
        // Monday-first week, exactly as the product brief's Mon–Sun bar chart.
        val monday = java.time.LocalDate.ofEpochDay(today)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekly = (0L..6L).map { offset ->
            val day = monday.plusDays(offset).toEpochDay()
            val row = byDay[day]
            DailyReading(
                epochDay = day,
                pages = row?.pages ?: 0,
                chapters = row?.chapters ?: 0,
                activeMillis = row?.activeMillis ?: 0L,
            )
        }
        val totalXp = totals.xp + bonusXp
        return JourneyStats(
            totalPages = totals.pages,
            weightedPages = totals.weightedPages,
            readingUnits = (totals.weightedPages / ReadingWeight.STANDARD_PAGES_PER_CHAPTER).toInt(),
            totalChapters = totals.chapters,
            activeMillis = totals.activeMillis,
            completedSeries = totals.completedSeries,
            activeDays = totals.activeDays,
            currentStreak = streak.current,
            longestStreak = streak.longest,
            streakGraceAvailable = streak.graceAvailable,
            distinctGenres = totals.genres,
            westernSeries = totals.westernSeries,
            nightChapters = totals.nightChapters,
            weekendChapters = totals.weekendChapters,
            totalXp = totalXp,
            level = LevelProgression.stateForXp(totalXp),
            favoriteGenre = favoriteGenre,
            weekly = weekly,
        )
    }

    private fun buildProgress(
        stats: JourneyStats,
        unlocked: Map<String, Set<BadgeTier>>,
    ): List<BadgeProgress> = Badges.ALL
        .sortedWith(compareBy({ it.category.ordinal }, { it.thresholds.first() }, { it.id }))
        .map { definition -> Badges.progress(definition, stats, unlocked[definition.id].orEmpty()) }

    private fun unlockedMap(rows: List<UnlockedBadgeEntity>): Map<String, Set<BadgeTier>> =
        rows.groupBy { it.badgeId }.mapValues { (_, entries) ->
            entries.mapNotNull { runCatching { BadgeTier.valueOf(it.tier) }.getOrNull() }.toSet()
        }

    /** Used by the session tracker after a completion. */
    suspend fun recordActivity(
        mangaId: String,
        seriesTitle: String,
        genre: String,
        readingMode: com.yomira.core.model.ReadingMode,
        pagesRead: Int,
        xpPagesRead: Int = pagesRead,
        chapterCompleted: Boolean,
        seriesCompleted: Boolean,
        firstRead: Boolean,
        activeMillis: Long,
        isWestern: Boolean,
    ) {
        val now = clock.nowMillis()
        val today = clock.todayEpochDay()
        val hour = clock.hourOfDay()
        val dayOfWeek = java.time.LocalDate.ofEpochDay(today).dayOfWeek
        val readingUnits = ReadingWeight.readingUnits(pagesRead, readingMode)
        val xp = if (chapterCompleted) ReadingWeight.chapterXp(xpPagesRead, readingMode, firstRead) else 0
        activityDao.insert(
            ReadingActivityEntity(
                seriesId = mangaId,
                seriesTitle = seriesTitle,
                genre = genre,
                readingMode = readingMode,
                occurredAt = now,
                dayEpoch = today,
                hourOfDay = hour,
                pageCount = pagesRead,
                chapterCount = if (chapterCompleted && firstRead) 1 else 0,
                activeMillis = activeMillis,
                weightedPages = readingUnits * ReadingWeight.STANDARD_PAGES_PER_CHAPTER,
                xp = xp,
                completedSeries = if (chapterCompleted && firstRead && seriesCompleted) 1 else 0,
                isWestern = isWestern,
                isNight = hour in 0..4,
                isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY,
            ),
        )
        profileDao.touch(today, now)
    }
}
