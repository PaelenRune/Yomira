package com.yomira.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Editorial knowledge for the western-comics shelf.
 *
 * The bundled catalogue already tags its western records with [MARKER]; remote
 * providers are less explicit, so the same decision is also made from the
 * publisher (`Marvel`, `DC`, `Image`, `Dark Horse`), the publication format
 * (`Comic`/`Graphic Novel`) and a curated list of marquee series.
 *
 * The rules are deliberately conservative: a Japanese manga that merely carries
 * a `Superhero` genre must stay on the manga shelf, while `Vinland Saga` must
 * not be promoted just because its title contains `Saga`.
 */
object WesternComics {
    /** Genre marker persisted for every series that belongs to this shelf. */
    const val MARKER = "Western Comic"

    /** Exact section title requested by the product copy. */
    const val SPOTLIGHT_TITLE = "🇺🇸 Amerikan & Batı Çizgi Roman Dünyası"

    /** Sub-line under the spotlight title. */
    const val SPOTLIGHT_SUBTITLE = "Marvel · DC · Image · Dark Horse"

    val PUBLISHERS: Set<String> = setOf(
        "marvel", "marvel comics", "dc", "dc comics", "vertigo", "wildstorm",
        "image", "image comics", "dark horse", "dark horse comics", "skybound",
        "boom studios", "boom! studios", "idw", "idw publishing", "dynamite",
        "dynamite entertainment", "oni press", "archie comics", "valiant",
        "valiant comics", "avatar press", "fantagraphics", "drawn quarterly",
        "titan comics", "2000 ad", "rebellion",
    )

    val GENRE_MARKERS: Set<String> = setOf(
        "western comic", "western comics", "american comic", "american comics",
        "graphic novel", "graphic novels", "comic", "comics",
    )

    val COMIC_TYPES: Set<String> = setOf(
        "comic", "comics", "graphic novel", "graphic novels", "trade paperback",
        "bande dessinee", "bd",
    )

    /**
     * Marquee order is intentional: the spotlight rail should open with the
     * household names (Spider-Man, Batman, Superman…) before the wider shelf.
     */
    val MARQUEE: List<String> = listOf(
        "spider-man", "batman", "superman", "avengers", "x-men",
        "invincible", "the boys", "watchmen", "the sandman", "hellboy",
        "saga", "daredevil", "deadpool", "justice league", "wonder woman",
        "the walking dead", "teen titans", "green lantern", "the flash",
        "iron man", "captain america", "fantastic four", "wolverine",
        "harley quinn", "preacher", "kick-ass", "scott pilgrim", "monstress",
        "y the last man", "fables", "hellblazer", "east of west",
        "something is killing the children", "black hammer", "descender",
        "locke & key", "paper girls", "v for vendetta",
    )

    /**
     * Short words that are also common manga-title tokens (`Saga`, `Watchmen`).
     * They only match an exact title, never a substring of another series.
     */
    private val EXACT_ONLY = setOf("saga", "the boys", "the sandman", "watchmen")

    /**
     * Compiled once for the lifetime of the process. `Regex` construction goes
     * through ICU `Pattern.compile`, which is expensive; the classifier is called
     * for every row of the catalogue on the composition thread, so compiling per
     * call used to stall the UI for seconds.
     */
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    /** [MARQUEE] in normalized form, preserving the curated order for [rank]. */
    private val MARQUEE_ALIASES: List<Alias> = MARQUEE.map { Alias(normalize(it), it in EXACT_ONLY) }

    private data class Alias(val text: String, val exactOnly: Boolean)

    /** True when a persisted catalogue row belongs to the western shelf. */
    fun matches(manga: Manga): Boolean = matches(
        genres = manga.genres,
        publicationType = null,
        publisher = null,
        titles = listOf(manga.title) + manga.altTitles,
    )

    /**
     * Pure classifier shared by the persisted catalogue and incoming remote
     * records. [publisher] and [publicationType] are optional because Room only
     * stores the normalised genres; remote metadata still carries the richer
     * evidence and gets marked on the way in.
     */
    fun matches(
        genres: List<String>,
        publicationType: String?,
        publisher: String?,
        titles: List<String>,
    ): Boolean {
        if (genres.any { normalize(it) in GENRE_MARKERS }) return true
        if (publisher != null && normalize(publisher) in PUBLISHERS) return true
        if (publicationType != null && normalize(publicationType) in COMIC_TYPES) return true
        return titles.any { title ->
            val normalizedTitle = normalize(title)
            MARQUEE_ALIASES.any { alias -> aliasMatches(normalizedTitle, alias) }
        }
    }

    /** Position in the marquee list, or [Int.MAX_VALUE] for the wider shelf. */
    fun rank(manga: Manga): Int {
        val titles = (listOf(manga.title) + manga.altTitles).map(::normalize)
        val match = MARQUEE_ALIASES.indexOfFirst { alias -> titles.any { aliasMatches(it, alias) } }
        return if (match < 0) Int.MAX_VALUE else match
    }

    /**
     * The editorial rail: marquee names in product order, then remaining
     * western records by score. Explicit records never reach this public method
     * because callers pass the already-filtered public catalogue.
     *
     * The rank is computed once per record before sorting: the previous
     * implementation re-ran the whole alias scan inside the comparator, which
     * cost O(n log n) classifications per composition.
     */
    fun spotlight(manga: List<Manga>, limit: Int = 14): List<Manga> {
        require(limit > 0) { "Spotlight limit must be positive" }
        return manga.asSequence()
            .filter(::matches)
            .distinctBy { it.id }
            .map { record -> record to rank(record) }
            .sortedWith(
                compareBy<Pair<Manga, Int>>(
                    { it.second },
                    { -(it.first.score ?: 0.0) },
                    { it.first.title.lowercase(Locale.ROOT) },
                ),
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Appends [MARKER] once so the record survives the genre-only Room round-trip. */
    fun tag(genres: List<String>): List<String> {
        val cleaned = genres.map { it.trim() }.filter { it.isNotEmpty() }
        return if (cleaned.any { normalize(it) == normalize(MARKER) }) cleaned else cleaned + MARKER
    }

    /**
     * Word-boundary alias match without regex construction: [normalize] already
     * collapses every non-alphanumeric run to a single space and trims the ends,
     * so padding both sides with a space is equivalent to `(^| )alias( |$)`.
     */
    private fun aliasMatches(normalizedTitle: String, alias: Alias): Boolean {
        if (alias.text.isEmpty()) return false
        if (alias.exactOnly) return normalizedTitle == alias.text
        return " $normalizedTitle ".contains(" ${alias.text} ")
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(NON_ALNUM, " ")
        .trim()
}
