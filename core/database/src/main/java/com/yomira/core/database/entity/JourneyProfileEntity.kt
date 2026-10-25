package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single local row for journey-wide values that cannot be derived from raw
 * activity: XP awarded by badges and the last day the profile was touched.
 *
 * Streaks themselves are derived from [ReadingActivityEntity] so the flexible
 * rest-day rule can evolve without corrupting historical data.
 */
@Entity(tableName = "journey_profile")
data class JourneyProfileEntity(
    @PrimaryKey val id: Int = SINGLE_PROFILE_ID,
    val bonusXp: Int = 0,
    val lastActiveDayEpoch: Long = -1L,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val SINGLE_PROFILE_ID = 1
    }
}
