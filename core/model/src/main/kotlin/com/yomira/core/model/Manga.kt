package com.yomira.core.model

/** Metadata only. URLs are references, not downloaded media or chapter payloads. */
data class Manga(
    val id: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val synopsis: String? = null,
    val score: Double? = null,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val genres: List<String> = emptyList(),
    val contentRating: ContentRating = ContentRating.SAFE,
    val sourceId: String,
    val publicationType: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Manga ID must not be blank" }
        require(title.isNotBlank()) { "Manga title must not be blank" }
        require(sourceId.isNotBlank()) { "Source ID must not be blank" }
        require(score == null || (score.isFinite() && score in 0.0..10.0)) {
            "Score must be normalized to 0..10"
        }
    }
}
