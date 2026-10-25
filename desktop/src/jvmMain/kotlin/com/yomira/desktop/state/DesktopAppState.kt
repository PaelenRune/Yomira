package com.yomira.desktop.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.desktop.data.CatalogRepository
import com.yomira.desktop.data.ChapterRepository
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.model.ReaderSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Top-level desktop surfaces. */
enum class DesktopScreen { BROWSE, DETAIL, READER }

/** What fills the poster grid: the discovery feed or one search result set. */
sealed interface FeedSource {
    data object Browse : FeedSource

    @Immutable
    data class Search(val query: String) : FeedSource
}

/**
 * State holder of the desktop shell.
 *
 * It owns navigation, async loads and their error states, but no Compose UI and
 * no network client — both boundaries are interfaces, so the paging and layer
 * behaviour can be unit tested with fakes. State mutations always run on the
 * scope's dispatcher (the Compose main scope in production).
 */
class DesktopAppState(
    private val catalog: CatalogRepository,
    private val chapters: ChapterRepository,
    private val scope: CoroutineScope,
    private val pageSize: Int = CatalogRepository.DEFAULT_PAGE_SIZE,
) {
    var screen by mutableStateOf(DesktopScreen.BROWSE)
        private set

    var feedSource by mutableStateOf<FeedSource>(FeedSource.Browse)
        private set

    var query by mutableStateOf("")
        private set

    var isSearchFocused by mutableStateOf(false)

    var items by mutableStateOf<List<CatalogItem>>(emptyList())
        private set

    var cursor by mutableStateOf(PageCursor.First)
        private set

    var feedLoading by mutableStateOf(false)
        private set

    var feedError by mutableStateOf<String?>(null)
        private set

    /** Grid filter chip: only series the desktop reader can open natively. */
    var readableOnly by mutableStateOf(false)
        private set

    /** The rows the poster wall actually renders. */
    val visibleItems: List<CatalogItem>
        get() = if (readableOnly) items.filter(CatalogItem::isReadableOnDesktop) else items

    var selectedItem by mutableStateOf<CatalogItem?>(null)
        private set

    var detail by mutableStateOf<Manga?>(null)
        private set

    var detailLoading by mutableStateOf(false)
        private set

    var detailError by mutableStateOf<String?>(null)
        private set

    var chapterList by mutableStateOf<List<DesktopChapter>>(emptyList())
        private set

    var chaptersLoading by mutableStateOf(false)
        private set

    var chaptersMessage by mutableStateOf<String?>(null)
        private set

    var reader by mutableStateOf<ReaderSession?>(null)
        private set

    var readerPage by mutableIntStateOf(0)
        private set

    /** Incremented whenever a new page of the grid arrives; the UI scrolls to top. */
    var gridResetToken by mutableIntStateOf(0)
        private set

    val hasLayer: Boolean get() = screen != DesktopScreen.BROWSE

    val canGoNext: Boolean
        get() = feedSource is FeedSource.Browse && !feedLoading && cursor.hasNext

    val canGoPrevious: Boolean
        get() = feedSource is FeedSource.Browse && !feedLoading && cursor.hasPrevious

    private var feedJob: Job? = null
    private var detailJob: Job? = null
    private var chapterJob: Job? = null
    private var pageJob: Job? = null

    /** First paint: one discovery page. Idempotent while a request is in flight. */
    fun start() {
        if (items.isEmpty() && !feedLoading && feedError == null) loadBrowse(PageCursor.First)
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun toggleReadableOnly() {
        readableOnly = !readableOnly
    }

    fun submitSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            clearSearch()
            return
        }
        feedJob?.cancel()
        feedSource = FeedSource.Search(trimmed)
        cursor = PageCursor.First
        items = emptyList()
        feedError = null
        feedLoading = true
        gridResetToken++
        feedJob = scope.launch {
            val result = try {
                catalog.search(trimmed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (result == null) {
                feedError = DesktopCopy.SEARCH_FAILED
            } else {
                items = result
            }
            feedLoading = false
        }
    }

    fun clearSearch() {
        feedJob?.cancel()
        query = ""
        feedSource = FeedSource.Browse
        cursor = PageCursor.First
        loadBrowse(PageCursor.First)
    }

    fun retry() {
        when (val source = feedSource) {
            FeedSource.Browse -> loadBrowse(cursor)
            is FeedSource.Search -> {
                query = source.query
                submitSearch()
            }
        }
    }

    fun nextPage() {
        if (!canGoNext) return
        loadBrowse(cursor.next())
    }

    fun previousPage() {
        if (!canGoPrevious) return
        loadBrowse(cursor.previous())
    }

    /**
     * Layer dismissal order: reader -> detail -> grid. Returns `true` when a
     * layer was closed, so the key handler can consume the event.
     */
    fun back(): Boolean = when (screen) {
        DesktopScreen.READER -> {
            closeReader()
            true
        }
        DesktopScreen.DETAIL -> {
            closeDetail()
            true
        }
        DesktopScreen.BROWSE -> false
    }

    fun openDetail(item: CatalogItem) {
        if (screen == DesktopScreen.DETAIL && selectedItem?.id == item.id) return
        closeReader()
        detailJob?.cancel()
        chapterJob?.cancel()
        selectedItem = item
        screen = DesktopScreen.DETAIL
        detail = null
        detailError = null
        detailLoading = true
        chapterList = emptyList()
        chaptersMessage = null
        chaptersLoading = item.isReadableOnDesktop

        detailJob = scope.launch {
            detail = try {
                catalog.detail(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                detailError = DesktopCopy.DETAIL_FAILED
                null
            }
            detailLoading = false
        }

        if (!item.isReadableOnDesktop) {
            chaptersMessage = DesktopCopy.CHAPTERS_UNSUPPORTED
            chaptersLoading = false
            return
        }
        chapterJob = scope.launch {
            chapterList = try {
                chapters.chapters(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                chaptersMessage = DesktopCopy.CHAPTERS_FAILED
                emptyList()
            }
            if (chapterList.isEmpty() && chaptersMessage == null) {
                chaptersMessage = DesktopCopy.CHAPTERS_EMPTY
            }
            chaptersLoading = false
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        chapterJob?.cancel()
        closeReader()
        screen = DesktopScreen.BROWSE
        selectedItem = null
        detail = null
        detailError = null
        chapterList = emptyList()
        chaptersMessage = null
        chaptersLoading = false
        detailLoading = false
    }

    /**
     * Opens a readable chapter. External (scanlator-hosted) chapters must be
     * launched in the browser by the UI and never reach this method.
     */
    fun openChapter(chapter: DesktopChapter) {
        val item = selectedItem ?: return
        if (!chapter.isReadable) return
        pageJob?.cancel()
        readerPage = 0
        screen = DesktopScreen.READER
        reader = ReaderSession(item = item, chapter = chapter, loading = true)
        pageJob = scope.launch {
            val pages = try {
                chapters.pages(chapter)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val active = reader
                if (active?.chapter?.id == chapter.id) {
                    reader = active.copy(loading = false, error = error.message ?: DesktopCopy.PAGES_FAILED)
                }
                return@launch
            }
            val active = reader
            if (active?.chapter?.id == chapter.id) {
                reader = active.copy(pages = pages, loading = false, error = null)
            }
        }
    }

    fun retryPages() {
        val chapter = reader?.chapter ?: return
        openChapter(chapter)
    }

    fun closeReader() {
        pageJob?.cancel()
        if (screen == DesktopScreen.READER) screen = DesktopScreen.DETAIL
        reader = null
        readerPage = 0
    }

    fun updateReaderPage(index: Int) {
        readerPage = index.coerceAtLeast(0)
    }

    private fun loadBrowse(target: PageCursor) {
        feedJob?.cancel()
        feedSource = FeedSource.Browse
        feedLoading = true
        feedError = null
        feedJob = scope.launch {
            val result = try {
                catalog.browse(target, pageSize)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (result == null) {
                feedError = DesktopCopy.FEED_FAILED
                feedLoading = false
                return@launch
            }
            items = result.items
            cursor = target.withNext(result.hasNext)
            gridResetToken++
            feedLoading = false
        }
    }
}

/** Hero copy of the selected series, preferring the merged waterfall detail. */
fun DesktopAppState.heroItem(): CatalogItem? {
    val item = selectedItem ?: return null
    val merged = detail ?: return item
    return item.copy(
        title = merged.title,
        coverUrl = merged.coverUrl ?: item.coverUrl,
        bannerUrl = merged.bannerUrl ?: item.bannerUrl,
        score = merged.score ?: item.score,
        status = if (merged.status != MangaStatus.UNKNOWN) merged.status else item.status,
        synopsis = merged.synopsis ?: item.synopsis,
        genres = merged.genres.ifEmpty { item.genres },
        author = merged.author ?: item.author,
        sourceId = merged.sourceId,
    )
}
