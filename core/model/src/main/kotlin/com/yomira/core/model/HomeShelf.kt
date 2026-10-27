package com.yomira.core.model

import java.util.Locale
import java.text.Normalizer
import kotlin.random.Random

/** Stable identities: selection belongs to the session, never to a composition or timer. */
enum class HomeShelf(val title: String, private vararg val tags: String) {
    POPULAR("Popüler Seriler"),
    PERSONAL("Size Özel"),
    WESTERN("Çizgi Roman & Western"),
    ACTION("Aksiyon & Shonen Zirvesi", "action", "aksiyon", "shonen", "shounen"),
    ROMANCE("Romantizm & Yaşamdan Kesitler", "romance", "romantic", "romantizm", "slice of life"),
    MYSTERY("Gizem, Gerilim & Psikolojik", "mystery", "thriller", "psychological", "gizem", "gerilim", "psikolojik"),
    ISEKAI("Isekai & Reenkarnasyon", "isekai", "reincarnation", "reenkarnasyon", "another world", "parallel world", "game world"),
    HORROR("Korku & Karanlık Fantezi", "horror", "dark fantasy", "korku", "karanlık fantezi"),
    SCI_FI("Bilim Kurgu & Cyberpunk", "sci fi", "science fiction", "cyberpunk", "bilim kurgu"),
    MARTIAL_ARTS("Dövüş Sanatları & Murim", "martial arts", "murim", "wuxia", "dövüş sanatları"),
    WEBTOON("Webtoon Hitleri (Dikey Okuma)", "webtoon", "webtoons", "manhwa", "pornhwa", "long strip", "longstrip", "vertical", "dikey okuma"),
    SEINEN("Seinen Başyapıtları", "seinen"),
    COMEDY("Komedi & Günlük Yaşam", "comedy", "komedi", "slice of life", "günlük yaşam"),
    COMPLETED("Ödüllü & Tamamlanmış Seriler", "award winning", "award winner", "ödüllü"),
    DISCOVERY("Yeni Eklenenler & Keşif");

    /** Matches persisted metadata, not title keywords or unrelated fallback titles. */
    fun matches(manga: Manga): Boolean = when (this) {
        POPULAR, PERSONAL, DISCOVERY -> true
        WESTERN -> WesternComics.matches(manga) ||
            manga.publicationType?.let { normalize(it) in WesternComics.COMIC_TYPES } == true
        WEBTOON -> matchesLabels(manga.genres + listOfNotNull(manga.publicationType))
        COMPLETED -> manga.status == MangaStatus.COMPLETED || matchesTags(manga)
        else -> matchesTags(manga)
    }

    // Format metadata never stands in for actual genre/demographic evidence.
    private fun matchesTags(manga: Manga): Boolean = matchesLabels(manga.genres)

    private fun matchesLabels(labels: List<String>): Boolean = labels.any { genre ->
        val normalized = normalize(genre)
        tags.any { tag -> " $normalized ".contains(" $tag ") }
    }

    companion object {
        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

        private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(NON_ALNUM, " ").trim()

        const val DYNAMIC_COUNT = 5
        val fixed: List<HomeShelf> = listOf(POPULAR, PERSONAL)
        val dynamicPool: List<HomeShelf> = entries.filterNot { it in fixed }

        /** Sampling without replacement; injectable random makes the contract testable. */
        fun select(random: Random = Random.Default): List<HomeShelf> =
            fixed + dynamicPool.shuffled(random).take(DYNAMIC_COUNT)
    }
}

data class HomeRail(val shelf: HomeShelf, val items: List<Manga>)
