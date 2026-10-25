package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yomira.core.database.entity.UnlockedBadgeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnlockedBadgeDao {
    @Query("SELECT * FROM unlocked_badges ORDER BY unlockedAt DESC")
    fun observeAll(): Flow<List<UnlockedBadgeEntity>>

    @Query("SELECT * FROM unlocked_badges ORDER BY unlockedAt DESC")
    suspend fun all(): List<UnlockedBadgeEntity>

    @Query("SELECT * FROM unlocked_badges WHERE unlockedAt >= :fromMillis AND unlockedAt < :toMillis ORDER BY unlockedAt DESC")
    suspend fun between(fromMillis: Long, toMillis: Long): List<UnlockedBadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(badge: UnlockedBadgeEntity): Long
}
