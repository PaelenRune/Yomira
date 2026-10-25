package com.yomira.domain.journey

/**
 * Everything the profile screen needs, already computed from local rows.
 *
 * [readingUnits] is the fair volume metric: 20 weighted pages = one standard
 * chapter. A 100-page western album is roughly 5.9 units, a 10-page manga
 * chapter 0.5 units, so badges and XP reflect effort instead of counting every
 * "chapter" equally.
 */
data class JourneyStats(
    val totalPages: Int = 0,
    val weightedPages: Double = 0.0,
    val readingUnits: Int = 0,
    val totalChapters: Int = 0,
    val activeMillis: Long = 0L,
    val completedSeries: Int = 0,
    val activeDays: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakGraceAvailable: Boolean = true,
    val distinctGenres: Int = 0,
    val westernSeries: Int = 0,
    val nightChapters: Int = 0,
    val weekendChapters: Int = 0,
    val totalXp: Int = 0,
    val level: LevelState = LevelProgression.stateForXp(0),
    val favoriteGenre: String? = null,
    val weekly: List<DailyReading> = emptyList(),
)

data class DailyReading(
    val epochDay: Long,
    val pages: Int,
    val chapters: Int,
    val activeMillis: Long,
)

data class JourneyDashboard(
    val stats: JourneyStats,
    val badges: List<BadgeProgress> = emptyList(),
    val unlockedCount: Int = 0,
    val totalBadgeCount: Int = Badges.ALL.size * BadgeTier.entries.size,
)
