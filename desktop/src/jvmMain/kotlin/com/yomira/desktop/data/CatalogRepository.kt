package com.yomira.desktop.data

import com.yomira.core.model.Manga
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopPage
import com.yomira.desktop.state.PageCursor

/** One browse page plus the source's own continuation signal. */
data class CatalogPage(
    val items: List<CatalogItem>,
    val hasNext: Boolean,
)

/**
 * Read-only catalogue boundary of the desktop shell.
 *
 * The implementation talks to the shared metadata waterfall; the UI and its
 * state holder only know this interface, which keeps paging, search and error
 * behaviour testable without a network.
 */
interface CatalogRepository {
    suspend fun browse(cursor: PageCursor, pageSize: Int = DEFAULT_PAGE_SIZE): CatalogPage

    /** Multi-provider search; results are ranked and de-duplicated by title identity. */
    suspend fun search(query: String): List<CatalogItem>

    /** Canonical, waterfall-merged detail for the selected card; `null` when the series vanished. */
    suspend fun detail(item: CatalogItem): Manga?

    companion object {
        const val DEFAULT_PAGE_SIZE = 32
        const val MAX_PAGE_SIZE = 50
        const val SEARCH_RESULT_LIMIT = 60
    }
}

/** Chapter and page-image boundary of the desktop reader. */
interface ChapterRepository {
    /** Chapters in reading order. Empty when the source cannot serve them on desktop. */
    suspend fun chapters(item: CatalogItem): List<DesktopChapter>

    suspend fun pages(chapter: DesktopChapter): List<DesktopPage>
}
