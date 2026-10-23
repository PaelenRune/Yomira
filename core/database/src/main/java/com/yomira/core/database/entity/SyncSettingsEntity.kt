package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table holding the preferences that participate in cloud sync. */
@Entity(tableName = "sync_settings")
data class SyncSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val gridColumns: Int = 3,
    val adultEnabled: Boolean = false,
    val theme: String = "oled",
    /** UTC epoch milliseconds of the last preference change. */
    val updatedAt: Long = 0L,
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}
