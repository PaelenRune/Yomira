package com.yomira.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelTest {
    @Test
    fun `content rating levels are stable`() {
        assertEquals(0, ContentRating.SAFE.level)
        assertEquals(1, ContentRating.MATURE.level)
        assertEquals(2, ContentRating.EXPLICIT.level)
        ContentRating.entries.forEach { assertEquals(it, ContentRating.fromLevel(it.level)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown rating is not silently downgraded to safe`() {
        ContentRating.fromLevel(3)
    }

    @Test
    fun `unknown metadata stays absent`() {
        val manga = Manga(id = "source:1", title = "Title", sourceId = "source")
        assertNull(manga.score)
        assertNull(manga.coverUrl)
        assertEquals(MangaStatus.UNKNOWN, manga.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `score requires normalized range`() {
        Manga(id = "source:1", title = "Title", sourceId = "source", score = 101.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `chapter rejects non finite number`() {
        Chapter(id = "source:1", mangaId = "source:manga", chapterNumber = Double.NaN, url = "https://example.org/1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sync version cannot be negative`() {
        SyncState(deviceId = "device", version = -1)
    }
}
