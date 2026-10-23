package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val deviceId: String,
    val lastSyncedAt: Date? = null,
    val version: Long = 0,
)
