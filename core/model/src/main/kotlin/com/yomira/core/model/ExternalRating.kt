package com.yomira.core.model

/**
 * Normalised four-tier content rating reported by an upstream catalogue.
 *
 * This is evidence, not the final Yomira rating. [com.yomira.core.model.ContentRating]
 * is still derived by the classifier so that multiple sources and publication
 * context can be combined.
 */
enum class ExternalRating(val apiValue: String) {
    SAFE("safe"),
    SUGGESTIVE("suggestive"),
    EROTICA("erotica"),
    PORNOGRAPHIC("pornographic");

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
    }
}
