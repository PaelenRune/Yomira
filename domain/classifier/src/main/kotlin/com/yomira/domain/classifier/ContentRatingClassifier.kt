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
 * Provider signals first collapse into a tier through
 * [ExternalRating.tier][com.yomira.core.model.ExternalRating.tier] (single
 * mapping layer, cautious-upper-wins on conflicts), then this classifier applies
 * publication context on top.
 *
 * EXPLICIT covers standalone hentai, pornhwa tags, hardcore or genitalia
 * evidence, explicit doujinshi and sources that only ever host adult comics
 * (`allporncomic`, `porncomix`, 3D porn sites). Everything soft — ecchi, fanservice,
 * suggestive romance, western erotica without hardcore evidence and serialized stories
 * with mature themes — stays MATURE in the public catalogue instead
 * of leaking into the +18 Vault.
 * Serialized-fiction context can correct broad provider/hentai labels, but never
 * direct explicit tags or an adult-only source.
 */
class ContentRatingClassifier {
    fun classify(input: ClassificationInput): ContentRating {
        // Providers flatten several tags into one delimited string ("Romance, Hentai");
        // split first, then match on padded word boundaries so "Japanese Hentai Manga"
        // matches the hentai label but "Nonhentai" never does.
        val labels = (input.genres + input.tags)
            .flatMap { it.split(Regex("[,;|/]+")) }
            .map { " ${normalize(it)} " }
        val hentai = labels.containsAny(setOf("hentai"))
        val explicitDoujin = hentai && (labels.containsAny(setOf("doujin", "doujinshi")) ||
            normalize(input.publicationType.orEmpty()) in setOf("doujin", "doujinshi"))
        if (labels.containsAny(explicitLabels) || explicitDoujin ||
            normalize(input.sourceId.orEmpty()).split(' ').any { it in adultSourceTokens }
        ) {
            return ContentRating.EXPLICIT
        }
        // A small, exact identity correction handles known bad provider records even
        // after caching has dropped their original publication metadata. It never
        // overrides direct explicit evidence above; arbitrary title keywords do not count.
        val knownSerializedErotica = (listOf(input.title) + input.altTitles)
            .any { normalize(it) in serializedEroticaTitles }
        val fictionContext = input.isOfficialPublication == true ||
            labels.containsAny(serializedFictionLabels) || knownSerializedErotica
        // All provider signals resolve through the shared tier mapping; the cautious
        // upper tier wins, so one pornographic catalogue outranks a safe one.
        val evidenceTier = ExternalRating.strongestTier(input.externalRatings)
        val providerPornographic = evidenceTier == ContentRating.EXPLICIT
        if (fictionContext && (hentai || providerPornographic || knownSerializedErotica)) {
            return ContentRating.MATURE
        }
        if (providerPornographic) return ContentRating.EXPLICIT
        // Verified non-pornographic ratings cannot be escalated by an unverified
        // hentai label. SAFE does not erase softer sexual evidence, however.
        if (evidenceTier != null) {
            return if (labels.containsAny(matureLabels) || evidenceTier >= ContentRating.MATURE)
                ContentRating.MATURE else ContentRating.SAFE
        }
        // Without contradictory publication/provider evidence, Hentai is explicit.
        if (hentai) return ContentRating.EXPLICIT
        return if (labels.containsAny(matureLabels)) ContentRating.MATURE else ContentRating.SAFE
    }

    private fun List<String>.containsAny(phrases: Set<String>): Boolean =
        any { label -> phrases.any { phrase -> " $phrase " in label } }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Hardcore pornography only. Anything softer must stay MATURE so it never reaches the Vault. */
    private val explicitLabels = setOf(
        // Japanese, Korean and Western adult comics; word matching also covers
        // "3D Porn", "Porn Comic", etc. Hentai is evaluated with fiction context above.
        "porn", "pornhwa", "pornography", "pornographic", "hardcore",
        "genitalia", "explicit sexual content", "explicit sex", "uncensored sex", "explicit doujinshi", "explicit doujin",
        // Adult-only comic platforms that scrapers flatten into genres/tags.
        "allporncomic", "allporncomics", "all porn comic", "all porn comics", "porncomix", "porcomix", "porcat",
    )
    /** Soft labels; Hentai additionally requires the contradictory evidence checked above. */
    private val matureLabels = setOf(
        "ecchi", "hentai", "mature", "adult", "smut", "sexual themes", "sexual content", "erotica", "erotic",
        "suggestive", "fanservice", "nudity", "softcore", "nsfw", "18",
        "adult webtoon", "adult webtoons", "adult comic", "adult comics",
        "western erotica", "erotic comics", "erotic manga",
        "3d comic", "3d comics", "r18",
    )
    /** Source ids of adult-only platforms; scraping one is pornographic evidence by itself. */
    private val adultSourceTokens = setOf(
        "hentai", "porn", "pornhwa", "allporncomic", "porncomix", "porcomix", "porcat",
        "nhentai", "hentai2read", "hentaifox", "asmhentai", "rule34", "manhwa18",
    )
    private val serializedFictionLabels = setOf(
        "isekai", "action", "adventure", "comedy", "school life", "gyaru", "romance",
        "fantasy", "supernatural", "shounen", "seinen",
    )
    private val serializedEroticaTitles = setOf(
        "I Formed a MILF Party with the Heroes' Mothers",
        "I Formed a MILF Party with the Heroes Mothers",
        "I Formed a MILF Party with the Hero's Mothers",
        "Yuusha no Haha Oya to Party wo Kumimashita",
        "Yuusha no Hahaoya-tachi to Party wo Kumimashita",
        "Yuusha ni Zenbu Ubawareta Ore wa Yuusha no Hahaoya to Party wo Kumimashita",
        "Betrayed by the Hero, I Formed a MILF Party with His Mom",
    ).map(::normalize).toSet()
}
