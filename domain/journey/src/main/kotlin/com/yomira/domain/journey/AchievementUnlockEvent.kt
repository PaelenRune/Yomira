package com.yomira.domain.journey

/** A transient celebration, emitted only when a badge tier is newly persisted. */
data class AchievementUnlockEvent(
    val badgeId: String,
    val tier: BadgeTier,
    val titleTr: String,
    val titleEn: String,
    val emoji: String,
    val xpBonus: Int,
    val descriptionTr: String,
    val descriptionEn: String,
)
