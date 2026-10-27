package com.yomira.feature.home

import com.yomira.core.model.Chapter
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LibraryUpdatesTest {
    private val manga = Manga("series", "Series", sourceId = "test")
    private fun chapter(number: Double, date: String? = null) = Chapter("c$number", manga.id, number,
        releaseDate = date?.let(Instant::parse), url = "https://example.com/$number")

    @Test fun `only chapters beyond reader watermark are new`() {
        val chapters = listOf(chapter(1.0), chapter(2.0), chapter(2.5), chapter(3.0))
        assertEquals(listOf(2.5, 3.0), unreadLibraryChapters(chapters, emptySet(), 2.0).map { it.chapterNumber })
    }

    @Test fun `manual read through watermark takes precedence`() {
        val chapters = listOf(chapter(1.0), chapter(2.0), chapter(3.0))
        assertEquals(listOf(chapters.last()), unreadLibraryChapters(chapters, setOf("c2.0"), 1.0))
        assertTrue(unreadLibraryChapters(chapters, chapters.map { it.id }.toSet(), null).isEmpty())
    }

    @Test fun `unstarted library includes all known chapters without duplicates`() {
        val first = chapter(1.0)
        assertEquals(listOf(first), unreadLibraryChapters(listOf(first, first), emptySet(), null))
        assertTrue(unreadLibraryChapters(emptyList(), emptySet(), null).isEmpty())
    }

    @Test fun `newest dated arrivals come first and explicit titles never leak`() {
        val old = LibraryUpdateUi(manga, chapter(1.0, "2026-01-01T00:00:00Z"))
        val fresh = LibraryUpdateUi(manga, chapter(2.0, "2026-01-02T00:00:00Z"))
        val unknown = LibraryUpdateUi(manga, chapter(3.0))
        val adult = LibraryUpdateUi(manga.copy(contentRating = ContentRating.EXPLICIT), chapter(4.0))
        assertEquals(listOf(fresh, old, unknown), chronologicalLibraryUpdates(listOf(unknown, old, adult, fresh, fresh)))
    }
}
