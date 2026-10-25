package com.yomira.domain.journey

/** Four product tiers shared by every badge. XP is paid once per tier. */
enum class BadgeTier(val labelTr: String, val labelEn: String, val xp: Int) {
    BRONZE("Bronz", "Bronze", 50),
    SILVER("Gümüş", "Silver", 125),
    GOLD("Altın", "Gold", 300),
    PLATINUM("Platin", "Platinum", 750),
}

enum class BadgeCategory(val titleTr: String, val titleEn: String) {
    READING_VOLUME("Okuma Miktarı", "Reading Volume"),
    CONSISTENCY("İstikrar", "Consistency"),
    EXPLORATION("Keşif", "Exploration"),
    SPECIAL("Özel", "Special"),
}

/**
 * A badge definition carries four thresholds, one per [BadgeTier], plus the
 * metric it reads from [JourneyStats]. Keeping metrics as pure functions makes
 * the whole achievement engine unit-testable without Room or Android.
 */
data class BadgeDefinition(
    val id: String,
    val category: BadgeCategory,
    val titleTr: String,
    val titleEn: String,
    val descriptionTr: String,
    val descriptionEn: String,
    val emoji: String,
    val thresholds: List<Int>,
    val metric: (JourneyStats) -> Int,
) {
    init {
        require(thresholds.size == BadgeTier.entries.size) { "A badge needs one threshold per tier" }
        require(thresholds.zipWithNext().all { (a, b) -> b > a }) { "Thresholds must strictly increase" }
    }
}

data class BadgeProgress(
    val badge: BadgeDefinition,
    val current: Int,
    val currentTier: BadgeTier?,
    val nextTarget: Int?,
    val progress: Float,
    val unlockedTiers: Set<BadgeTier>,
) {
    val isUnlocked: Boolean get() = unlockedTiers.isNotEmpty()
    val isFullyUnlocked: Boolean get() = unlockedTiers.size == BadgeTier.entries.size
}

object Badges {
    const val FIRST_STEP = "first_step"
    const val HUNDREDS_CLUB = "hundreds_club"
    const val LEGENDARY_READER = "legendary_reader"
    const val FLAME_STREAK = "flame_streak"
    const val GENRE_EXPLORER = "genre_explorer"
    const val WESTERN_EXPLORER = "western_explorer"
    const val NIGHT_OWL = "night_owl"
    const val WEEKEND_MARATHONER = "weekend_marathoner"

    val ALL: List<BadgeDefinition> = listOf(
        BadgeDefinition(
            id = FIRST_STEP,
            category = BadgeCategory.READING_VOLUME,
            titleTr = "İlk Adım",
            titleEn = "First Step",
            descriptionTr = "İlk okuma birimini tamamla. Yolculuk bir sayfayla başlar.",
            descriptionEn = "Complete your first reading unit. Every journey starts with one page.",
            emoji = "🌱",
            thresholds = listOf(1, 10, 50, 150),
            metric = { it.readingUnits },
        ),
        BadgeDefinition(
            id = HUNDREDS_CLUB,
            category = BadgeCategory.READING_VOLUME,
            titleTr = "Yüzler Kulübü",
            titleEn = "Hundreds Club",
            descriptionTr = "100 standart bölüm değerinde okuma biriktir.",
            descriptionEn = "Bank 100 standard chapters worth of reading.",
            emoji = "💯",
            thresholds = listOf(100, 250, 500, 1000),
            metric = { it.readingUnits },
        ),
        BadgeDefinition(
            id = LEGENDARY_READER,
            category = BadgeCategory.READING_VOLUME,
            titleTr = "Efsane Okur",
            titleEn = "Legendary Reader",
            descriptionTr = "1.000 birim okumayla efsaneler arasına katıl.",
            descriptionEn = "Join the legends with 1,000 reading units.",
            emoji = "🐉",
            thresholds = listOf(1000, 2500, 5000, 10000),
            metric = { it.readingUnits },
        ),
        BadgeDefinition(
            id = FLAME_STREAK,
            category = BadgeCategory.CONSISTENCY,
            titleTr = "Alev Serisi",
            titleEn = "Flame Streak",
            descriptionTr = "7 gün boyunca okuma ritmini koru. Her hafta bir dinlenme günü hediye.",
            descriptionEn = "Keep a seven-day rhythm. One rest day per week is on us.",
            emoji = "🔥",
            thresholds = listOf(7, 14, 30, 100),
            metric = { it.currentStreak },
        ),
        BadgeDefinition(
            id = GENRE_EXPLORER,
            category = BadgeCategory.EXPLORATION,
            titleTr = "Tür Gezgini",
            titleEn = "Genre Explorer",
            descriptionTr = "5 farklı türde okuma yaparak rafını genişlet.",
            descriptionEn = "Widen your shelf by reading five different genres.",
            emoji = "🧭",
            thresholds = listOf(5, 10, 20, 30),
            metric = { it.distinctGenres },
        ),
        BadgeDefinition(
            id = WESTERN_EXPLORER,
            category = BadgeCategory.EXPLORATION,
            titleTr = "Batı Kaşifi",
            titleEn = "Western Explorer",
            descriptionTr = "Marvel/DC dünyasından seriler keşfet.",
            descriptionEn = "Discover series from the Marvel/DC universe.",
            emoji = "🦸",
            thresholds = listOf(1, 5, 15, 30),
            metric = { it.westernSeries },
        ),
        BadgeDefinition(
            id = NIGHT_OWL,
            category = BadgeCategory.SPECIAL,
            titleTr = "Gece Kuşu",
            titleEn = "Night Owl",
            descriptionTr = "00:00–05:00 arasında 50 bölüm oku. Sessizliğin ustası ol.",
            descriptionEn = "Read 50 chapters between 00:00 and 05:00. Master the silence.",
            emoji = "🌙",
            thresholds = listOf(50, 150, 400, 1000),
            metric = { it.nightChapters },
        ),
        BadgeDefinition(
            id = WEEKEND_MARATHONER,
            category = BadgeCategory.SPECIAL,
            titleTr = "Haftasonu Maratoncusu",
            titleEn = "Weekend Marathoner",
            descriptionTr = "Hafta sonları 20 bölüm okuyarak maratonunu tamamla.",
            descriptionEn = "Complete your marathon with 20 weekend chapters.",
            emoji = "🏁",
            thresholds = listOf(20, 100, 250, 500),
            metric = { it.weekendChapters },
        ),
    )

    fun byId(id: String): BadgeDefinition? = ALL.firstOrNull { it.id == id }

    fun progress(
        definition: BadgeDefinition,
        stats: JourneyStats,
        unlockedTiers: Set<BadgeTier>,
    ): BadgeProgress {
        val current = definition.metric(stats).coerceAtLeast(0)
        val metricIndex = definition.thresholds.indexOfLast { current >= it }
        val earnedIndex = unlockedTiers.mapNotNull { tier -> BadgeTier.entries.indexOf(tier).takeIf { it >= 0 } }
            .maxOrNull() ?: -1
        val displayIndex = maxOf(metricIndex, earnedIndex)
        val nextIndex = displayIndex + 1
        val start = if (displayIndex >= 0) definition.thresholds[displayIndex] else 0
        val next = definition.thresholds.getOrNull(nextIndex)
        val fraction = if (next == null) 1f
        else ((current - start).toFloat() / (next - start).toFloat()).coerceIn(0f, 1f)
        return BadgeProgress(
            badge = definition,
            current = current,
            currentTier = BadgeTier.entries.getOrNull(displayIndex),
            nextTarget = next,
            progress = fraction,
            unlockedTiers = unlockedTiers,
        )
    }
}
