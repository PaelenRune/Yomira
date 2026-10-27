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
)

/** Evidence-based routing, not age verification. Erotica and age labels are not proof of pornography. */
class ContentRatingClassifier {
    fun classify(input: ClassificationInput): ContentRating {
        // These serialized stories are frequently mislabeled by upstream providers.
        // Apply the correction to both primary and translated titles before provider ratings.
        val knownStory = (input.altTitles + input.title).any { title ->
            val normalized = normalize(title)
            normalized in matureStories || matureStoryPrefixes.any { prefix ->
                normalized == prefix || normalized.startsWith("$prefix ")
            }
        }
        if (knownStory) return ContentRating.MATURE

        val labels = (input.genres + input.tags)
            .flatMap { it.split(Regex("[,;|/]+")) }
            .map(::normalize).toSet()
        if (ExternalRating.PORNOGRAPHIC in input.externalRatings || labels.any { it in explicitLabels }) {
            return ContentRating.EXPLICIT
        }
        // Official status and format (manga, manhwa, webtoon, doujinshi, etc.)
        // establish neither the presence nor absence of explicit sexual content.
        return if (labels.any { it in matureLabels } ||
            input.externalRatings.any { it == ExternalRating.SUGGESTIVE || it == ExternalRating.EROTICA }
        ) ContentRating.MATURE else ContentRating.SAFE
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val explicitLabels = setOf(
        "hentai", "hentai manga", "hentai doujinshi", "hentai doujin", "porn", "pornography",
        "pornographic", "pornhwa", "hardcore", "hardcore porn", "explicit sexual content",
        "explicit sex", "uncensored genitalia", "explicit genitalia",
    )
    private val matureLabels = setOf(
        "ecchi", "mature", "adult", "smut", "sexual themes", "erotica", "suggestive",
        "adult webtoon", "adult webtoons", "fanservice", "nudity", "sexual content",
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
