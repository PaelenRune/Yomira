package com.yomira.feature.journey

/**
 * Texts and share-card labels for the cinematic Wrapped summary.
 *
 * The Turkish defaults keep previews/tests independent of Android resources;
 * [english] provides the fully localized counterpart used when the app locale
 * is English. Numbers and durations are formatted here so the screen and the
 * exported PNG card always agree.
 */
data class WrappedCopy(
    val english: Boolean = false,
    val title: String = "Yomira Wrapped",
    val monthly: String = "Aylık",
    val yearly: String = "Yıllık",
    val chaptersHeadline: String = "Bu ay %1\$d bölüm okudun!",
    val chaptersHeadlineYearly: String = "Bu yıl %1\$d bölüm okudun!",
    val chaptersSubline: String = "%1\$s sayfa · %2\$d okuma birimi",
    val timeHeadline: String = "%1\$s aktif okuma",
    val timeSubline: String = "%1\$d aktif gün boyunca",
    val topGenreHeadline: String = "En sevdiğin tür",
    val topGenreEmpty: String = "Henüz bir tür öne çıkmadı",
    val topSeriesHeadline: String = "En çok okuduğun seri",
    val topSeriesEmpty: String = "Bu dönemde seri tamamlanmadı",
    val growthHeadline: String = "Geçen döneme göre",
    val growthUp: String = "+%1\$d%% büyüme!",
    val growthDown: String = "%1\$d%% değişim",
    val growthFirst: String = "İlk dönemin — bu bir başlangıç!",
    val levelHeadline: String = "Seviye %1\$d · %2\$s",
    val levelSubline: String = "%1\$s toplam XP",
    val badgesHeadline: String = "Bu dönem kazanılan rozetler",
    val badgesEmpty: String = "Yeni rozet yok; bir sonraki hedef seni bekliyor.",
    val completedSeries: String = "%1\$d seri tamamlandı",
    val share: String = "Paylaş",
    val shareChooser: String = "Yomira Wrapped kartını paylaş",
    val shareFailed: String = "Paylaşım kartı oluşturulamadı.",
    val close: String = "Kapat",
    val emptyTitle: String = "Bu dönem henüz sessiz",
    val emptyBody: String = "Birkaç bölüm okuyup geri döndüğünde burada sinematik bir hikâye bulacaksın.",
    val shareCardTag: String = "YOMIRA WRAPPED",
    val pageHint: String = "%1\$d / %2\$d",
    val monthNames: List<String> = listOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
    ),
    val monthNamesEn: List<String> = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ),
    val finalHeadline: String = "Bu hikâye senin.",
    val finalBody: String = "Her sayfa, her gece, her maraton… Yomira senin ritminle büyüyor.",
    val finalCta: String = "Kartı paylaş",
) {
    fun formatNumber(value: Int): String {
        val locale = if (english) java.util.Locale.US else java.util.Locale("tr", "TR")
        return java.text.NumberFormat.getIntegerInstance(locale).format(value)
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

    fun periodLabel(summary: com.yomira.domain.journey.WrappedSummary): String = when (summary.period) {
        com.yomira.domain.journey.WrappedPeriod.MONTHLY -> {
            val names = if (english) monthNamesEn else monthNames
            "${names.getOrElse(summary.reference.monthValue - 1) { "" }} ${summary.reference.year}"
        }
        com.yomira.domain.journey.WrappedPeriod.YEARLY -> summary.reference.year.toString()
    }

    companion object {
        /** Fully localized English instance; the no-arg Turkish defaults stay for previews/tests. */
        fun english(): WrappedCopy = WrappedCopy(
            english = true,
            monthly = "Monthly",
            yearly = "Yearly",
            chaptersHeadline = "You read %1\$d chapters this month!",
            chaptersHeadlineYearly = "You read %1\$d chapters this year!",
            chaptersSubline = "%1\$s pages · %2\$d reading units",
            timeHeadline = "%1\$s of active reading",
            timeSubline = "across %1\$d active days",
            topGenreHeadline = "Your favourite genre",
            topGenreEmpty = "No genre stands out yet",
            topSeriesHeadline = "Your most-read series",
            topSeriesEmpty = "No series finished this period",
            growthHeadline = "Compared to last period",
            growthUp = "+%1\$d%% growth!",
            growthDown = "%1\$d%% change",
            growthFirst = "Your first period — this is a beginning!",
            levelHeadline = "Level %1\$d · %2\$s",
            levelSubline = "%1\$s lifetime XP",
            badgesHeadline = "Badges earned this period",
            badgesEmpty = "No new badges yet; the next goal is waiting.",
            completedSeries = "%1\$d series finished",
            share = "Share",
            shareChooser = "Share your Yomira Wrapped card",
            shareFailed = "The share card could not be created.",
            close = "Close",
            emptyTitle = "This period is quiet",
            emptyBody = "Read a few chapters and come back for a cinematic story.",
            shareCardTag = "YOMIRA WRAPPED",
            pageHint = "%1\$d / %2\$d",
            finalHeadline = "This story is yours.",
            finalBody = "Every page, every night, every marathon — Yomira grows with your rhythm.",
            finalCta = "Share the card",
        )
    }
}
