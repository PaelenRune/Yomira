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

/** Evidence-based routing, not age verification. Explicit evidence always wins. */
class ContentRatingClassifier {
    fun classify(input: ClassificationInput): ContentRating {
        val labels = (input.genres + input.tags).map(::normalize).toSet()
        if (ExternalRating.PORNOGRAPHIC in input.externalRatings || labels.any { it in explicitLabels }) {
            return ContentRating.EXPLICIT
        }
        val knownStory = (input.altTitles + input.title).any { normalize(it) in matureStories }
        val serializedStory = input.isOfficialPublication == true &&
            normalize(input.publicationType.orEmpty()) in setOf("manga", "comic") &&
            labels.any { it in setOf("ecchi", "romance", "drama", "comedy") }
        if ((ExternalRating.EROTICA in input.externalRatings || "erotica" in labels) &&
            !knownStory && !serializedStory
        ) return ContentRating.EXPLICIT
        return if (knownStory || labels.any { it in matureLabels } ||
            input.externalRatings.any { it == ExternalRating.SUGGESTIVE || it == ExternalRating.EROTICA }
        ) ContentRating.MATURE else ContentRating.SAFE
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val explicitLabels = setOf("hentai", "porn", "pornography", "pornographic", "pornhwa", "adult webtoon", "explicit sexual content")
    private val matureLabels = setOf("ecchi", "mature", "adult", "smut", "sexual themes", "erotica", "suggestive")
    private val matureStories = listOf(
        "I Formed a MILF Party with the Heroes' Mothers",
        "Kimi wa Midara na Boku no Joou",
        "Prison School",
        "Kangoku Gakuen",
    ).map(::normalize).toSet()
}
