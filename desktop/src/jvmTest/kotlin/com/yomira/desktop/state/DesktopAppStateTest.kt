package com.yomira.desktop.state

import com.yomira.core.model.Manga
import com.yomira.desktop.data.CatalogPage
import com.yomira.desktop.data.CatalogRepository
import com.yomira.desktop.data.ChapterRepository
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.model.DesktopPage
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shell navigation, paging and async guards, exercised against fakes so no
 * network or display is needed. Each test mirrors a user gesture: pressing the
 * arrow in the grid, opening a dossier, reading a chapter and pressing Escape.
 */
class DesktopAppStateTest {

    private class FakeCatalog(
        var pages: Map<Int, CatalogPage> = emptyMap(),
        var searchResults: List<CatalogItem> = emptyList(),
        var searchFailure: Boolean = false,
        var browseFailure: Boolean = false,
    ) : CatalogRepository {
        val browseCalls = mutableListOf<Int>()
        var lastSearch: String? = null

        override suspend fun browse(cursor: PageCursor, pageSize: Int): CatalogPage {
            browseCalls += cursor.page
            if (browseFailure) error("offline")
            return pages[cursor.page] ?: CatalogPage(emptyList(), hasNext = false)
        }

        override suspend fun search(query: String): List<CatalogItem> {
            lastSearch = query
            if (searchFailure) error("offline")
            return searchResults
        }

        override suspend fun detail(item: CatalogItem): Manga? =
            Manga(id = item.id, title = item.title, author = "Oda", sourceId = item.sourceId)
    }

    private class FakeChapters(
        var chapterList: List<DesktopChapter> = emptyList(),
        var pages: List<DesktopPage> = emptyList(),
        var chapterFailure: Boolean = false,
        var pageFailure: Boolean = false,
    ) : ChapterRepository {
        val pageRequests = mutableListOf<String>()

        override suspend fun chapters(item: CatalogItem): List<DesktopChapter> {
            if (chapterFailure) error("feed unavailable")
            return chapterList
        }

        override suspend fun pages(chapter: DesktopChapter): List<DesktopPage> {
            pageRequests += chapter.id
            if (pageFailure) error("pages unavailable")
            return pages
        }
    }

    private fun item(id: String, title: String = "One Piece", source: String = "mangadex") = CatalogItem(
        id = "$source:$id",
        title = title,
        sourceId = source,
        remoteId = id,
    )

    private fun chapter(number: Double = 1.0) = DesktopChapter(
        id = "chapter-$number",
        mangaId = "1",
        number = number,
        pageCount = 2,
    )

    private fun pages() = listOf(
        DesktopPage(0, "https://cdn.example/1.png"),
        DesktopPage(1, "https://cdn.example/2.png"),
    )

    @Test
    fun `browse loads the first page and pages forward only when the source allows it`() = runTest {
        val catalog = FakeCatalog(
            pages = mapOf(
                1 to CatalogPage(listOf(item("1"), item("2")), hasNext = true),
                2 to CatalogPage(listOf(item("3")), hasNext = false),
            ),
        )
        val state = DesktopAppState(catalog, FakeChapters(), this)
        state.start()
        runCurrent()

        assertEquals(2, state.items.size)
        assertTrue(state.canGoNext)
        assertFalse(state.canGoPrevious)
        assertEquals(listOf(1), catalog.browseCalls)

        state.nextPage()
        runCurrent()
        assertEquals(2, state.cursor.page)
        assertEquals(listOf(1, 2), catalog.browseCalls)
        assertFalse("the last page must not offer another page", state.canGoNext)

        // Paging past the boundary is a no-op, not an extra request.
        state.nextPage()
        runCurrent()
        assertEquals(listOf(1, 2), catalog.browseCalls)

        state.previousPage()
        runCurrent()
        assertEquals(1, state.cursor.page)
        assertEquals(3, catalog.browseCalls.size)
    }

    @Test
    fun `search replaces the feed and dismissing it restores browse`() = runTest {
        val catalog = FakeCatalog(
            pages = mapOf(1 to CatalogPage(listOf(item("1")), hasNext = false)),
            searchResults = listOf(item("9", "Berserk")),
        )
        val state = DesktopAppState(catalog, FakeChapters(), this)
        state.start()
        runCurrent()

        state.updateQuery("  berserk  ")
        state.submitSearch()
        runCurrent()
        assertEquals("berserk", catalog.lastSearch)
        assertTrue(state.feedSource is FeedSource.Search)
        assertEquals(1, state.items.size)
        assertEquals("Berserk", state.items.single().title)
        assertFalse(state.canGoNext)

        state.clearSearch()
        runCurrent()
        assertTrue(state.feedSource is FeedSource.Browse)
        assertEquals("One Piece", state.items.single().title)
        assertEquals("", state.query)
    }

    @Test
    fun `empty search query falls back to the discovery feed`() = runTest {
        val catalog = FakeCatalog(pages = mapOf(1 to CatalogPage(listOf(item("1")), hasNext = false)))
        val state = DesktopAppState(catalog, FakeChapters(), this)
        state.start()
        runCurrent()

        state.submitSearch()
        runCurrent()
        assertTrue(state.feedSource is FeedSource.Browse)
        assertNull(catalog.lastSearch)
    }

    @Test
    fun `search and browse failures surface actionable copy and can be retried`() = runTest {
        val catalog = FakeCatalog(browseFailure = true, searchFailure = true)
        val state = DesktopAppState(catalog, FakeChapters(), this)
        state.start()
        runCurrent()
        assertEquals(DesktopCopy.FEED_FAILED, state.feedError)
        assertFalse(state.feedLoading)

        state.updateQuery("one piece")
        state.submitSearch()
        runCurrent()
        assertEquals(DesktopCopy.SEARCH_FAILED, state.feedError)

        catalog.searchFailure = false
        catalog.searchResults = listOf(item("1"))
        state.retry()
        runCurrent()
        assertNull(state.feedError)
        assertEquals(1, state.items.size)
    }

    @Test
    fun `detail merges the waterfall and lists real chapters`() = runTest {
        val catalog = FakeCatalog()
        val chapters = FakeChapters(chapterList = listOf(chapter(1.0), chapter(2.0)))
        val state = DesktopAppState(catalog, chapters, this)

        state.openDetail(item("1"))
        runCurrent()

        assertEquals(DesktopScreen.DETAIL, state.screen)
        assertTrue(state.hasLayer)
        assertNotNull(state.detail)
        assertEquals("Oda", state.detail?.author)
        assertEquals(2, state.chapterList.size)
        assertFalse(state.chaptersLoading)
        assertNull(state.chaptersMessage)
    }

    @Test
    fun `non mangadex cards state the desktop limitation instead of faking chapters`() = runTest {
        val chapters = FakeChapters()
        val state = DesktopAppState(FakeCatalog(), chapters, this)

        state.openDetail(item("44", source = "anilist"))
        runCurrent()

        assertEquals(DesktopScreen.DETAIL, state.screen)
        assertTrue(state.chapterList.isEmpty())
        assertEquals(DesktopCopy.CHAPTERS_UNSUPPORTED, state.chaptersMessage)
        assertFalse(state.chaptersLoading)
    }

    @Test
    fun `chapter feed failure keeps the dossier usable`() = runTest {
        val chapters = FakeChapters(chapterFailure = true)
        val state = DesktopAppState(FakeCatalog(), chapters, this)

        state.openDetail(item("1"))
        runCurrent()

        assertEquals(DesktopCopy.CHAPTERS_FAILED, state.chaptersMessage)
        assertFalse(state.chaptersLoading)
        assertEquals(DesktopScreen.DETAIL, state.screen)
    }

    @Test
    fun `reading a chapter loads pages and returns to the dossier on escape`() = runTest {
        val chapters = FakeChapters(chapterList = listOf(chapter(1.0)), pages = pages())
        val state = DesktopAppState(FakeCatalog(), chapters, this)
        state.openDetail(item("1"))
        runCurrent()

        state.openChapter(chapter(1.0))
        runCurrent()

        assertEquals(DesktopScreen.READER, state.screen)
        assertEquals(2, state.reader?.pages?.size)
        assertFalse(state.reader?.loading ?: true)
        assertEquals(listOf("chapter-1.0"), chapters.pageRequests)

        assertTrue("Escape must close the reader", state.back())
        assertEquals(DesktopScreen.DETAIL, state.screen)
        assertNull(state.reader)

        assertTrue("the next Escape must close the dossier", state.back())
        assertEquals(DesktopScreen.BROWSE, state.screen)
        assertFalse("the grid has no layer left to close", state.back())
    }

    @Test
    fun `external chapters never open the in app reader`() = runTest {
        val chapters = FakeChapters(pages = pages())
        val state = DesktopAppState(FakeCatalog(), chapters, this)
        state.openDetail(item("1"))
        runCurrent()

        state.openChapter(chapter(1.0).copy(externalUrl = "https://scan.example/1"))
        runCurrent()

        assertEquals(DesktopScreen.DETAIL, state.screen)
        assertTrue(chapters.pageRequests.isEmpty())
    }

    @Test
    fun `page failure is retryable without losing the session`() = runTest {
        val chapters = FakeChapters(chapterList = listOf(chapter(1.0)), pages = pages(), pageFailure = true)
        val state = DesktopAppState(FakeCatalog(), chapters, this)
        state.openDetail(item("1"))
        runCurrent()

        state.openChapter(chapter(1.0))
        runCurrent()
        assertEquals("pages unavailable", state.reader?.error)

        chapters.pageFailure = false
        state.retryPages()
        runCurrent()
        assertNull(state.reader?.error)
        assertEquals(2, state.reader?.pages?.size)
    }

    @Test
    fun `reader page selection is clamped`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.updateReaderPage(4)
        assertEquals(4, state.readerPage)
        state.updateReaderPage(-3)
        assertEquals(0, state.readerPage)
    }

    @Test
    fun `reader skips to the adjacent readable chapter and stops at the ends`() = runTest {
        val readable = listOf(chapter(1.0), chapter(2.0), chapter(3.0))
        val chapters = FakeChapters(
            chapterList = readable + chapter(4.0).copy(externalUrl = "https://scan.example/4"),
            pages = pages(),
        )
        val state = DesktopAppState(FakeCatalog(), chapters, this)
        state.openDetail(item("1"))
        runCurrent()
        state.openChapter(readable[1])
        runCurrent()

        // The external chapter is not offered by the capsule.
        assertEquals(listOf("chapter-1.0", "chapter-2.0", "chapter-3.0"), state.readerChapters.map { it.id })
        assertNotNull(state.adjacentChapter(1))
        assertEquals("chapter-3.0", state.adjacentChapter(1)?.id)
        assertEquals("chapter-1.0", state.adjacentChapter(-1)?.id)

        state.openAdjacentChapter(1)
        runCurrent()
        assertEquals("chapter-3.0", state.reader?.chapter?.id)
        assertNull("the last readable chapter has no next step", state.adjacentChapter(1))
        assertTrue(chapters.pageRequests.contains("chapter-3.0"))
    }

    @Test
    fun `readable filter narrows the visible poster wall`() = runTest {
        val catalog = FakeCatalog(
            pages = mapOf(
                1 to CatalogPage(
                    listOf(item("1"), item("2", "Berserk", source = "anilist")),
                    hasNext = false,
                ),
            ),
        )
        val state = DesktopAppState(catalog, FakeChapters(), this)
        state.start()
        runCurrent()
        assertEquals(2, state.visibleItems.size)

        state.toggleReadableOnly()
        assertEquals(1, state.visibleItems.size)
        assertTrue(state.visibleItems.single().isReadableOnDesktop)

        state.toggleReadableOnly()
        assertEquals(2, state.visibleItems.size)
    }
}

