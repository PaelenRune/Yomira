package com.yomira.core.model

import java.time.Instant

/** A chapter reference; no page URLs, image bytes or chapter text are stored. */
data class Chapter(
    val id: String,
    val mangaId: String,
    val chapterNumber: Double,
    val title: String? = null,
    val releaseDate: Instant? = null,
    val scanlationGroup: String? = null,
    val url: String,
) {
    init {
        require(id.isNotBlank()) { "Chapter ID must not be blank" }
        require(mangaId.isNotBlank()) { "Manga ID must not be blank" }
        require(chapterNumber.isFinite() && chapterNumber >= 0) {
            "Chapter number must be finite and non-negative"
        }
        require(url.isNotBlank()) { "Chapter URL must not be blank" }
    }
}
