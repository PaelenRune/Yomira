package com.yomira.core.model

/** Stable levels for storage and parental controls; never persist enum ordinals. */
enum class ContentRating(val level: Int) {
    SAFE(0),
    MATURE(1),
    EXPLICIT(2);

    companion object {
        fun fromLevel(level: Int): ContentRating = entries.firstOrNull { it.level == level }
            ?: throw IllegalArgumentException("Unknown content rating level: $level")
    }
}
