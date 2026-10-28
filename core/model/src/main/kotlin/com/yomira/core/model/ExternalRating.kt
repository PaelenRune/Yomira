package com.yomira.core.model

/**
 * Normalised four-tier content rating reported by an upstream catalogue.
 *
 * This is evidence, not the final Yomira rating. [com.yomira.core.model.ContentRating]
 * is still derived by the classifier so that multiple sources and publication
 * context can be combined.
 *
 * Provider signal mapping (v8.2.0 tier model):
 * - MangaDex `contentRating`: safe → [SAFE], suggestive → [SUGGESTIVE],
 *   erotica → [EROTICA], pornographic → [PORNOGRAPHIC].
 * - AniList: `isAdult` → [PORNOGRAPHIC]; ecchi/nudity/sexual-theme tags are
 *   folded into [SUGGESTIVE] by the adapter.
 * - Kitsu: `ageRating` G/PG → [SAFE], R (R17) → [SUGGESTIVE] — or [PORNOGRAPHIC]
 *   when the record also carries the `nsfw` flag — and R18 → [PORNOGRAPHIC].
 * - MangaUpdates (Baka): the Mature/Adult genre labels flow as text labels.
 */
enum class ExternalRating(val apiValue: String) {
    SAFE("safe"),
    SUGGESTIVE("suggestive"),
    EROTICA("erotica"),
    PORNOGRAPHIC("pornographic");

    /**
     * The content tier this evidence pushes toward:
     * SAFE → [ContentRating.SAFE], SUGGESTIVE/EROTICA → [ContentRating.MATURE],
     * PORNOGRAPHIC → [ContentRating.EXPLICIT].
     *
     * Soft adult evidence (erotica) deliberately stays MATURE so serialized
     * +18-themed comics remain on the public 18+ rails instead of leaking into
     * the +18 Vault; [ContentRatingClassifier] may still escalate with harder
     * evidence. Conflicting provider signals resolve through [strongestTier],
     * where the cautious upper tier wins.
     */
    val tier: ContentRating
        get() = when (this) {
            SAFE -> ContentRating.SAFE
            SUGGESTIVE, EROTICA -> ContentRating.MATURE
            PORNOGRAPHIC -> ContentRating.EXPLICIT
        }

    companion object {
        /**
         * Parses the MangaDex `contentRating` value. Unknown or blank values are
         * treated as absent evidence instead of being guessed as SAFE.
         */
        fun fromApiValue(value: String?): ExternalRating? = when (value?.trim()?.lowercase()) {
            null, "" -> null
            SAFE.apiValue -> SAFE
            SUGGESTIVE.apiValue -> SUGGESTIVE
            EROTICA.apiValue -> EROTICA
            PORNOGRAPHIC.apiValue -> PORNOGRAPHIC
            else -> null
        }

        /**
         * Combines every provider signal into one tier: the cautious upper tier
         * wins, so a SAFE label from one catalogue can never soften a
         * pornographic signal from another. Absent evidence yields null.
         */
        fun strongestTier(ratings: Collection<ExternalRating>): ContentRating? =
            ratings.maxOfOrNull { it.tier }
    }
}
