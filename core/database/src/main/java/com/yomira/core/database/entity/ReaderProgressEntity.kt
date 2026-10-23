package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Independent of chapter refreshes; indexes are zero-based. */
@Entity(tableName = "reader_progress", indices = [Index("mangaId")])
data class ReaderProgressEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val updatedAt: Long,
)
