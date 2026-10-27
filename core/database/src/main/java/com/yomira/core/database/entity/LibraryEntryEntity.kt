package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local library membership used by cloud sync.
 *
 * No foreign key to `manga` on purpose: a device must be able to receive a library
 * entry before metadata for that title has been fetched from a source. The row is a
 * user-state record, not a catalogue record.
 */
@Entity(tableName = "library_entries")
data class LibraryEntryEntity(
    @PrimaryKey val mangaId: String,
    val inLibrary: Boolean = true,
    val category: String? = null,
    /** UTC epoch milliseconds of the last local change. */
    val updatedAt: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val categories: List<String> = emptyList(),
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val customTags: List<String> = emptyList(),
)
