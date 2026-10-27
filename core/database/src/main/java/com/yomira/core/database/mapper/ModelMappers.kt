package com.yomira.core.database.mapper

import com.yomira.core.database.entity.ChapterEntity
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.database.entity.SyncStateEntity
import com.yomira.core.model.Chapter
import com.yomira.core.model.Manga
import com.yomira.core.model.ReadingMode
import com.yomira.core.model.SyncState
import java.util.Date

fun MangaEntity.toModel(): Manga = Manga(
    id = id,
    title = title,
    altTitles = altTitles,
    coverUrl = coverUrl,
    bannerUrl = bannerUrl,
    author = author,
    artist = artist,
    synopsis = synopsis,
    score = score,
    popularity = popularity,
    status = status,
    genres = genres,
    contentRating = contentRating,
    sourceId = sourceId,
)

/** When updating metadata, pass the existing reading mode to preserve the user's preference. */
fun Manga.toEntity(readingMode: ReadingMode = ReadingMode.MANGA_RTL): MangaEntity = MangaEntity(
    id = id,
    title = title,
    altTitles = altTitles,
    coverUrl = coverUrl,
    bannerUrl = bannerUrl,
    author = author,
    artist = artist,
    synopsis = synopsis,
    score = score,
    popularity = popularity,
    status = status,
    genres = genres,
    contentRating = contentRating,
    sourceId = sourceId,
    readingMode = readingMode,
)

fun ChapterEntity.toModel(): Chapter = Chapter(
    id = id,
    mangaId = mangaId,
    chapterNumber = chapterNumber,
    title = title,
    releaseDate = releaseDate?.toInstant(),
    scanlationGroup = scanlationGroup,
    url = url,
)

fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    mangaId = mangaId,
    chapterNumber = chapterNumber,
    title = title,
    releaseDate = releaseDate?.let(Date::from),
    scanlationGroup = scanlationGroup,
    url = url,
)

fun SyncStateEntity.toModel(): SyncState = SyncState(
    lastSyncedAt = lastSyncedAt?.toInstant(),
    deviceId = deviceId,
    version = version,
)

fun SyncState.toEntity(): SyncStateEntity = SyncStateEntity(
    deviceId = deviceId,
    lastSyncedAt = lastSyncedAt?.let(Date::from),
    version = version,
)
