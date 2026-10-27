package com.yomira.domain.classifier

import com.yomira.core.model.ContentRating
import com.yomira.core.model.ExternalRating
import java.text.Normalizer
import java.util.Locale

data class ClassificationInput(
    val title: String,
    val altTitles: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val externalRatings: List<ExternalRating> = emptyList(),
    val isOfficialPublication: Boolean? = null,
    val publicationType: String? = null,
    /**
     * Stable provider identifier (the `sourceId` half of a manga id). Sources that
     * host adult-only catalogues — `allporncomic`-type sites — are explicit evidence
     * on their own: nothing they list can be a general-audience series.
     */
    val sourceId: String? = null,
)

/**
 * Evidence-based routing, not age verification. Erotica and age labels are not proof of pornography.
 *
 * EXPLICIT is reserved for actual pornography: hentai/pornhwa tags, hardcore or genitalia
 * evidence, explicit doujinshi and sources that only ever host adult comics
 * (`allporncomic`, `porncomix`, 3D porn sites). Everything soft — ecchi, fanservice,
 * suggestive romance, western erotica without hardcore evidence and serialized stories
 * with mature themes like Prison School — stays MATURE in the public catalogue instead
 * of leaking into the +18 Vault.
 */
class ContentRatingClassifier {
    fun classify(input: ClassificationInput): ContentRating {
        // These serialized stories are frequently mislabeled by upstream providers.
        // Apply the correction to both primary and translated titles before provider
        // ratings, and before source detection: an adult aggregator hosting a
        // serialized story does not turn it into pornography.
        val knownStory = (input.altTitles + input.title).any { title ->
            val normalized = normalize(title)
            normalized in matureStories || matureStoryPrefixes.any { prefix ->
                normalized == prefix || normalized.startsWith("$prefix ")
            }
        }
        if (knownStory) return ContentRating.MATURE

        // Providers flatten several tags into one delimited string ("Romance, Hentai");
        // split first, then match on padded word boundaries so "Japanese Hentai Manga"
        // is hentai but "Nonhentai" never is.
        val labels = (input.genres + input.tags)
            .flatMap { it.split(Regex("[,;|/]+")) }
            .map { " ${normalize(it)} " }
        if (ExternalRating.PORNOGRAPHIC in input.externalRatings ||
            labels.containsAny(explicitLabels) ||
            normalize(input.sourceId.orEmpty()).split(' ').any { it in adultSourceTokens }
        ) {
            return ContentRating.EXPLICIT
        }
        // Official status and format (manga, manhwa, webtoon, doujinshi, etc.)
        // establish neither the presence nor absence of explicit sexual content.
        return if (labels.containsAny(matureLabels) ||
            input.externalRatings.any { it == ExternalRating.SUGGESTIVE || it == ExternalRating.EROTICA }
        ) ContentRating.MATURE else ContentRating.SAFE
    }

    private fun List<String>.containsAny(phrases: Set<String>): Boolean =
        any { label -> phrases.any { phrase -> " $phrase " in label } }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Hardcore pornography only. Anything softer must stay MATURE so it never reaches the Vault. */
    private val explicitLabels = setOf(
        // Japanese, Korean and Western adult comics; word matching also covers
        // "Hentai Manga", "Hentai Doujinshi", "3D Hentai", "3D Porn", "Porn Comic", …
        "hentai", "porn", "pornhwa", "pornography", "pornographic", "hardcore",
        "genitalia", "explicit sexual content", "explicit sex", "explicit doujinshi", "explicit doujin",
        // Adult-only comic platforms that scrapers flatten into genres/tags.
        "allporncomic", "allporncomics", "all porn comic", "all porn comics", "porncomix", "porcomix", "porcat",
    )
    /** Suggestive or softcore evidence. Public catalogue, never the Vault. */
    private val matureLabels = setOf(
        "ecchi", "mature", "adult", "smut", "sexual themes", "sexual content", "erotica", "erotic",
        "suggestive", "fanservice", "nudity", "softcore", "nsfw", "18",
        "adult webtoon", "adult webtoons", "adult comic", "adult comics",
        "western erotica", "erotic comics", "erotic manga",
        "3d comic", "3d comics", "r18",
    )
    /** Source ids of adult-only platforms; scraping one is pornographic evidence by itself. */
    private val adultSourceTokens = setOf(
        "hentai", "porn", "pornhwa", "allporncomic", "porncomix", "porcomix", "porcat",
        "nhentai", "hentai2read", "hentaifox", "asmhentai", "rule34",
    )
    private val matureStories = listOf(
        "I Formed a MILF Party with the Heroes' Mothers",
        "Kimi wa Midara na Boku no Joou",
        "Prison School",
        "Kangoku Gakuen",
        "Yuusha no Haha",
        "Yuusha ni Zenbu Ubawareta Ore wa Yuusha no Hahaoya to Party wo Kumimashita",
        "Betrayed by the Hero, I Formed a MILF Party with His Mom!",
    ).map(::normalize).toSet()
    private val matureStoryPrefixes = listOf(
        "I Formed a MILF Party with the Heroes' Mothers",
        "I Formed a MILF Party with the Hero's Mothers",
        "Yuusha no Haha",
        "Yuusha no Hahaoya",
        "Yuusha no Hahaoya tachi",
    ).map(::normalize)
}
