package com.yomira.core.model

/**
 * Franchise relationship of a connected series, mirroring AniList `MediaRelation`.
 * The detail page shows these as badge-labelled cards in the "Related series" strip.
 */
enum class RelationRole {
    /** Earlier entry of the same story, e.g. *Jujutsu Kaisen 0*. */
    PREQUEL,
    /** Continuation of the story, e.g. *Solo Leveling: Ragnarok*. */
    SEQUEL,
    /** Standalone side publication of the same franchise. */
    SPIN_OFF,
    /** Short side story inside the same continuity. */
    SIDE_STORY,
    /** Adaptation into another medium. */
    ADAPTATION,
    /** Alternative retelling of the same story. */
    ALTERNATIVE,
    /** The work this one is based on. */
    SOURCE,
    /** The umbrella entry this one belongs to. */
    PARENT,
    /** Character-focused entry. */
    CHARACTER,
    /** Relation AniList does not classify further. */
    OTHER,
}

/**
 * One franchise-relative series resolved from a provider relations graph.
 * Metadata only: URLs are remote references, exactly like [Manga].
 */
data class RelatedSeries(
    /** Stable shelf-style id, e.g. `anilist:101093`. */
    val id: String,
    val title: String,
    val role: RelationRole = RelationRole.OTHER,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Related series ID must not be blank" }
        require(title.isNotBlank()) { "Related series title must not be blank" }
    }
}

/**
 * One community recommendation: the "readers who enjoyed this also liked"
 * showcase, e.g. *Solo Leveling* → *Omniscient Reader's Viewpoint*.
 */
data class RecommendedSeries(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val score: Double? = null,
) {
    init {
        require(id.isNotBlank()) { "Recommended series ID must not be blank" }
        require(title.isNotBlank()) { "Recommended series title must not be blank" }
        require(score == null || (score.isFinite() && score in 0.0..10.0)) {
            "Score must be normalized to 0..10"
        }
    }
}

/** Both detail-page showcases of one series, fetched lazily from the relations graph. */
data class SeriesConnections(
    val related: List<RelatedSeries> = emptyList(),
    val recommended: List<RecommendedSeries> = emptyList(),
)