package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yomira.core.model.ContentRating
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode

/** IDs must be globally unique, e.g. "sourceId:remoteId". Only metadata is persisted. */
@Entity(
    tableName = "manga",
    indices = [Index("sourceId"), Index("title"), Index("contentRating")],
)
data class MangaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val synopsis: String? = null,
    val score: Double? = null,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val genres: List<String> = emptyList(),
    val contentRating: ContentRating = ContentRating.SAFE,
    val sourceId: String,
    val readingMode: ReadingMode = ReadingMode.MANGA_RTL,
)
