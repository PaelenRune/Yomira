package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Last reading position for a title. No foreign key to `manga` so progress received
 * from another device survives until the catalogue entry itself is available.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val mangaId: String,
    val chapterNumber: Double = 0.0,
    val pageIndex: Int = 0,
    /** UTC epoch milliseconds of the last read event. */
    val lastReadAt: Long = 0L,
)
