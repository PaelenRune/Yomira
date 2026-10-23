package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mangaId", "chapterNumber"])],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val chapterNumber: Double,
    val title: String? = null,
    val releaseDate: Date? = null,
    val scanlationGroup: String? = null,
    val url: String,
)
