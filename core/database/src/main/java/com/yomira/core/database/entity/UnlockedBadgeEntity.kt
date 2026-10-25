package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Persisted badge tier unlock. The composite key makes awarding idempotent:
 * inserting the same badge/tier twice is a no-op, which is what lets the
 * achievement engine run after every chapter without ever double-paying XP.
 */
@Entity(
    tableName = "unlocked_badges",
    primaryKeys = ["badgeId", "tier"],
    indices = [Index("unlockedAt")],
)
data class UnlockedBadgeEntity(
    val badgeId: String,
    val tier: String,
    val unlockedAt: Long,
)
