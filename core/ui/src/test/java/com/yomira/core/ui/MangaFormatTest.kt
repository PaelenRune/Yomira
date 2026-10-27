package com.yomira.core.ui

import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaFormatTest {

    private fun manga(genres: List<String>) = Manga(
        id = "series",
        title = "Series",
        genres = genres,
        sourceId = "test",
    )

    @Test fun `explicit webtoon genres classify as webtoon`() {
        assertEquals(MangaFormat.WEBTOON, mangaFormatOf(manga(listOf("Webtoon", "Romance"))))
        assertEquals(MangaFormat.WEBTOON, mangaFormatOf(manga(listOf("Manhwa"))))
    }

    @Test fun `manhua wins over the shared webtoon tag`() {
        assertEquals(MangaFormat.MANHUA, mangaFormatOf(manga(listOf("Webtoon", "Manhua"))))
    }

    @Test fun `western marker classifies as western comic`() {
        assertEquals(MangaFormat.WESTERN, mangaFormatOf(manga(listOf("Western Comic", "Action"))))
    }

    @Test fun `ordinary genres stay manga`() {
        assertEquals(MangaFormat.MANGA, mangaFormatOf(manga(listOf("Action", "Romance"))))
        assertEquals(MangaFormat.MANGA, mangaFormatOf(manga(emptyList())))
    }
}
