package com.yomira.feature.journey

import com.yomira.domain.journey.BadgeCategory
import com.yomira.domain.journey.BadgeDefinition
import com.yomira.domain.journey.BadgeTier
import java.text.NumberFormat
import java.util.Locale

/**
 * Localized texts and formatters for the profile/journey tab.
 *
 * Like the other feature modules this holds no Android resources; the app
 * builds one instance per composition so an EN/TR switch re-renders the whole
 * screen. Defaults are Turkish for previews and tests.
 */
data class JourneyCopy(
    val english: Boolean = false,
    val title: String = "Yolculuğun",
    val subtitle: String = "Her sayfa bir iz bırakır",
    val levelPrefix: String = "Seviye",
    val xpSuffix: String = "XP",
    val xpToNext: String = "%1\$s XP sonra %2\$s",
    val streakDays: String = "%1\$d gün seri",
    val streakGrace: String = "1 dinlenme günü hazır",
    val streakNoGrace: String = "Bu haftanın izni kullanıldı",
    val weeklyTitle: String = "Bu hafta",
    val weeklyChapters: String = "%1\$d bölüm",
    val weeklyEmpty: String = "Bu hafta henüz okuma yok. İlk sayfa seni bekliyor.",
    val statsTitle: String = "Tüm zamanlar",
    val statChapters: String = "Bölüm",
    val statPages: String = "Sayfa",
    val statTime: String = "Aktif süre",
    val statSeries: String = "Tamamlanan seri",
    val statDays: String = "Aktif gün",
    val statLongest: String = "En uzun seri",
    val statGenres: String = "Farklı tür",
    val statUnits: String = "birim",
    val favoriteGenre: String = "Favori tür",
    val unitHint: String = "20 sayfalık standart bölüm = 1 birim. Uzun bölümler daha ağır sayılır.",
    val badgesTitle: String = "Rozetler",
    val badgesSubtitle: String = "Kazanılan ve kilitli başarımlar",
    val badgesCount: String = "%1\$d / %2\$d rozet",
    val locked: String = "Kilitli",
    val maxTier: String = "Maksimum",
    val progressTo: String = "%1\$d / %2\$d",
    val wrappedCtaTitle: String = "Yomira Wrapped",
    val wrappedCtaBody: String = "Bu ayın hikâyesini sinematik bir özetle yeniden yaşa.",
    val wrappedCtaButton: String = "Özetimi aç",
    val emptyTitle: String = "Hikâyen yeni başlıyor",
    val emptyBody: String = "İlk bölümü okuduğunda seviyen, rozetlerin ve haftalık ritmin burada belirecek.",
    val back: String = "Geri",
    val maxLevel: String = "Maksimum seviye",
    val weekdays: List<String> = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"),
) {
    fun badgeTitle(badge: BadgeDefinition): String = if (english) badge.titleEn else badge.titleTr
    fun badgeDescription(badge: BadgeDefinition): String = if (english) badge.descriptionEn else badge.descriptionTr
    fun tierLabel(tier: BadgeTier): String = if (english) tier.labelEn else tier.labelTr
    fun categoryLabel(category: BadgeCategory): String = if (english) category.titleEn else category.titleTr

    fun formatNumber(value: Int): String {
        val locale = if (english) Locale.US else Locale("tr", "TR")
        return NumberFormat.getIntegerInstance(locale).format(value)
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> if (english) "${hours}h ${minutes}m" else "${hours} sa ${minutes} dk"
            hours > 0 -> if (english) "${hours}h" else "${hours} sa"
            else -> if (english) "${minutes}m" else "${minutes} dk"
        }
    }

    companion object {
        /** Full English counterpart of the Turkish defaults. */
        fun english(): JourneyCopy = JourneyCopy(
            english = true,
            title = "Your Journey",
            subtitle = "Every page leaves a mark",
            levelPrefix = "Level",
            xpToNext = "%1\$s XP to %2\$s",
            streakDays = "%1\$d day streak",
            streakGrace = "1 rest day available",
            streakNoGrace = "This week's rest day is used",
            weeklyTitle = "This week",
            weeklyChapters = "%1\$d chapters",
            weeklyEmpty = "No reading this week yet. Your first page is waiting.",
            statsTitle = "All time",
            statChapters = "Chapters",
            statPages = "Pages",
            statTime = "Active time",
            statSeries = "Series finished",
            statDays = "Active days",
            statLongest = "Longest streak",
            statGenres = "Genres",
            statUnits = "units",
            favoriteGenre = "Favourite genre",
            unitHint = "A standard 20-page chapter equals one unit. Longer chapters weigh more.",
            badgesTitle = "Badges",
            badgesSubtitle = "Earned and locked achievements",
            badgesCount = "%1\$d / %2\$d badges",
            locked = "Locked",
            maxTier = "Maxed out",
            wrappedCtaTitle = "Yomira Wrapped",
            wrappedCtaBody = "Relive this month as a cinematic story.",
            wrappedCtaButton = "Open my recap",
            emptyTitle = "Your story is just starting",
            emptyBody = "Read your first chapter and your level, badges and weekly rhythm will appear here.",
            back = "Back",
            maxLevel = "Max level",
            weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
        )
    }
}
