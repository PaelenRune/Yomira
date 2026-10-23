package com.yomira.core.database

import com.yomira.core.database.mapper.toEntity
import com.yomira.core.database.mapper.toModel
import com.yomira.core.model.Chapter
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import com.yomira.core.model.SyncState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMappersTest {
    @Test
    fun `manga round trip preserves every metadata field`() {
        val manga = Manga(
            id = "source:manga",
            title = "Title",
            altTitles = listOf("Alternative"),
            coverUrl = "https://example.org/cover",
            bannerUrl = "https://example.org/banner",
            author = "Author",
            artist = "Artist",
            synopsis = "Synopsis",
            score = 8.5,
            status = MangaStatus.ONGOING,
            genres = listOf("Adventure"),
            contentRating = ContentRating.MATURE,
            sourceId = "source",
        )
        val entity = manga.toEntity(ReadingMode.WEBTOON)
        assertEquals(ReadingMode.WEBTOON, entity.readingMode)
        assertEquals(manga, entity.toModel())
    }

    @Test
    fun `chapter and sync timestamps round trip at database millisecond precision`() {
        val instant = Instant.ofEpochMilli(1_700_000_000_123)
        val chapter = Chapter(
            id = "source:chapter",
            mangaId = "source:manga",
            chapterNumber = 12.5,
            title = "Title",
            releaseDate = instant,
            scanlationGroup = "Group",
            url = "https://example.org/chapter",
        )
        val sync = SyncState(lastSyncedAt = instant, deviceId = "device", version = 5)
        assertEquals(chapter, chapter.toEntity().toModel())
        assertEquals(sync, sync.toEntity().toModel())
        assertEquals(sync.copy(lastSyncedAt = null), sync.copy(lastSyncedAt = null).toEntity().toModel())
    }
}
