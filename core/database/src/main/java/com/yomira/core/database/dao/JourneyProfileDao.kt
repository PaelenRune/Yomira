package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.yomira.core.database.entity.JourneyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyProfileDao {
    @Query("SELECT * FROM journey_profile WHERE id = ${JourneyProfileEntity.SINGLE_PROFILE_ID}")
    fun observe(): Flow<JourneyProfileEntity?>

    @Query("SELECT * FROM journey_profile WHERE id = ${JourneyProfileEntity.SINGLE_PROFILE_ID}")
    suspend fun get(): JourneyProfileEntity?

    @Upsert
    suspend fun upsert(profile: JourneyProfileEntity)

    @Transaction
    suspend fun awardBonusXp(amount: Int, now: Long) {
        if (amount <= 0) return
        val current = get() ?: JourneyProfileEntity()
        upsert(current.copy(bonusXp = current.bonusXp + amount, updatedAt = now))
    }

    @Transaction
    suspend fun touch(dayEpoch: Long, now: Long) {
        val current = get() ?: JourneyProfileEntity()
        upsert(current.copy(lastActiveDayEpoch = dayEpoch, updatedAt = now))
    }
}
