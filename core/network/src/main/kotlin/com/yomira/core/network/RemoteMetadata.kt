package com.yomira.core.network

import com.yomira.core.model.ExternalRating
import com.yomira.core.model.MangaStatus

/**
 * Source-independent metadata record. Every client normalises its own payload
 * into this shape; the waterfall aggregator only works with this type.
 *
 * URLs are remote references only. No image bytes are downloaded or stored.
 */
@kotlinx.serialization.Serializable
data class RemoteMetadata(
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val altTitles: Map<String, List<String>> = emptyMap(),
    /**
     * The known English display title (`title.english` on AniList, `title["en"]`
     * on MangaDex, the canonical English title on MangaUpdates). Readers expect
     * *Attack on Titan*, never the romaji *Shingeki no Kyojin*, so the waterfall
     * aggregator promotes this value to the series display title whenever present.
     */
    val englishTitle: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val score: Double? = null,
    val popularity: Long? = null,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val primaryGenre: String? = null,
    val contentRating: ExternalRating? = null,
    val publicationType: String? = null,
    val publisher: String? = null,
    val isOfficialPublication: Boolean? = null,
    val year: Int? = null,
    val totalIssues: Int? = null,
    val sourceUrl: String? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "Source id must not be blank" }
        require(remoteId.isNotBlank()) { "Remote id must not be blank" }
        require(title.isNotBlank()) { "Title must not be blank" }
        require(score == null || (score.isFinite() && score in 0.0..10.0)) {
            "Score must be normalised to 0..10"
        }
        require(popularity == null || popularity >= 0) { "Popularity must not be negative" }
        require(year == null || year in 1800..2200) { "Year is out of range" }
    }

    /** Every known title, including the canonical one, de-duplicated case-insensitively. */
    fun allTitles(): List<String> {
        val seen = mutableSetOf<String>()
        return buildList {
            fun addTitle(value: String?) {
                val trimmed = value?.trim().orEmpty()
                if (trimmed.isNotEmpty() && seen.add(trimmed.lowercase(java.util.Locale.ROOT))) add(trimmed)
            }
            addTitle(title)
            addTitle(englishTitle)
            altTitles.values.forEach { values -> values.forEach(::addTitle) }
        }
    }
}
