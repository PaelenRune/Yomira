package com.yomira.feature.details

import com.yomira.core.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterFilterTest {
    private fun entry(number: Double, read: Boolean = false) = ChapterUiState(
        Chapter("chapter-$number", "manga", number, url = "https://example.com/$number"),
        read = read,
    )

    @Test fun `unread filter hides read chapters and preserves descending order`() {
        val chapters = listOf(entry(1.0), entry(3.0, read = true), entry(2.5))
        assertEquals(listOf(2.5, 1.0), visibleChapters(chapters, true, true).map { it.chapter.chapterNumber })
        assertEquals(3, chapters.size)
    }

    @Test fun `disabling unread filter restores read chapters in ascending order`() {
        val chapters = listOf(entry(3.0, read = true), entry(1.0), entry(2.0))
        assertEquals(listOf(1.0, 2.0, 3.0), visibleChapters(chapters, false, false).map { it.chapter.chapterNumber })
    }

    @Test fun `all read and empty lists produce no unread results`() {
        assertEquals(emptyList<ChapterUiState>(), visibleChapters(listOf(entry(1.0, true)), true, true))
        assertEquals(emptyList<ChapterUiState>(), visibleChapters(emptyList(), false, true))
    }

    @Test fun `read status updates are reflected by filter`() {
        val chapter = entry(1.0)
        assertEquals(listOf(chapter), visibleChapters(listOf(chapter), true, true))
        assertEquals(emptyList<ChapterUiState>(), visibleChapters(listOf(chapter.copy(read = true)), true, true))
    }

    @Test fun `read labels and filter copy can be localized`() {
        assertEquals("Okundu", DetailCopy().readLabel)
        assertEquals("Sadece Okunmamışlar", DetailCopy().unreadOnly)
        val english = DetailCopy(readLabel = "Read", unreadOnly = "Show Unread Only")
        assertEquals("Read", english.readLabel)
        assertEquals("Show Unread Only", english.unreadOnly)
    }
}
