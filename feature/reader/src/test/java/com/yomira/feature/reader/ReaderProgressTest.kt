package com.yomira.feature.reader

import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.core.model.ReadingMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReaderProgressTest {
    @Test fun `panning is bounded and resets at native scale`() {
        val size = androidx.compose.ui.unit.IntSize(100, 200)
        assertEquals(androidx.compose.ui.geometry.Offset(50f, -100f),
            clampPan(androidx.compose.ui.geometry.Offset(500f, -500f), 2f, size))
        assertEquals(androidx.compose.ui.geometry.Offset.Zero,
            clampPan(androidx.compose.ui.geometry.Offset(500f, -500f), 1f, size))
    }

    @Test fun `only manga turns pages right to left`() {
        assertTrue(ReadingMode.MANGA_RTL.isRightToLeft())
        assertFalse(ReadingMode.WESTERN_LTR.isRightToLeft())
        assertFalse(ReadingMode.WEBTOON.isRightToLeft())
    }

    @Test fun `restored index stays within a changed page list`() {
        assertEquals(0, boundedPage(-4, 45))
        assertEquals(44, boundedPage(99, 45))
        assertEquals(13, boundedPage(13, 45))
        assertEquals(0, boundedPage(4, 0))
    }

    @Test fun `page and chapter index survive reopening`() = runTest {
        val rows = mutableMapOf<String, ReaderProgressEntity>()
        var dirtyRevision = 0
        val store = ReaderProgressStore(read = { rows[it] }, write = { rows[it.chapterId] = it; dirtyRevision++ })
        val page14 = ReaderProgressEntity("chapter-7", "series", 6, 13, 45, 100)
        store.save(page14)
        assertEquals(page14, store.restore("chapter-7"))
        assertEquals(1, dirtyRevision)
        store.save(page14.copy(pageIndex = 14, updatedAt = 101))
        assertEquals(14, store.restore("chapter-7")?.pageIndex)
        assertEquals(6, store.restore("chapter-7")?.chapterIndex)
        assertEquals(2, dirtyRevision)
        assertNull(store.restore("unopened"))
    }

    @Test fun `different chapters retain independent positions`() = runTest {
        val rows = mutableMapOf<String, ReaderProgressEntity>()
        val store = ReaderProgressStore({ rows[it] }, { rows[it.chapterId] = it })
        store.save(ReaderProgressEntity("a", "series", 0, 3, 10, 1))
        store.save(ReaderProgressEntity("b", "series", 1, 5, 20, 2))
        assertEquals(3, store.restore("a")?.pageIndex)
        assertEquals(5, store.restore("b")?.pageIndex)
    }

    @Test fun `invalid progress never reaches persistence`() = runTest {
        var writes = 0
        val store = ReaderProgressStore({ null }, { writes++ })
        val base = ReaderProgressEntity("a", "series", 0, 0, 10, 1)
        for (invalid in listOf(base.copy(pageIndex = -1), base.copy(pageIndex = 10), base.copy(pageCount = 0), base.copy(chapterIndex = -1))) {
            try { store.save(invalid); fail("Expected invalid progress to be rejected") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, writes)
    }
}
