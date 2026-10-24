package com.yomira.app.catalog

import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.mapper.toModel
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.PageSlice
import com.yomira.core.model.PageWindow
import com.yomira.core.network.AniListClient
import com.yomira.core.network.ComicVineClient
import com.yomira.core.network.MangaDexClient
import com.yomira.core.network.OpenLibraryClient
import com.yomira.core.network.RemoteMetadata
import com.yomira.domain.classifier.ContentRatingClassifier
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** One page appended to the endless grid. */
data class CatalogPage(
    val page: Int,
    val items: List<Manga>,
    val hasMore: Boolean,
)

/**
 * Endless discovery engine.
 *
 * Every call fetches the next window from four independent catalogues in
 * parallel:
 *  - AniList GraphQL (`page` + `perPage: 25`),
 *  - MangaDex REST (`offset = (page - 1) * 25`),
 *  - ComicVine (`offset`, key-gated) for the western spotlight,
 *  - OpenLibrary (key-less) as the graphic-novel fallback.
 *
 * Records are merged before insert through [CatalogMerger], so a series that is
 * already in Room under its seed/romaji name is updated instead of duplicated.
 * A failing provider never aborts the page: the remaining providers still fill
 * the grid.
 */
class PagedCatalogLoader @Inject constructor(
    private val aniListClient: AniListClient,
    private val mangaDexClient: MangaDexClient,
    private val comicVineClient: ComicVineClient,
    private val openLibraryClient: OpenLibraryClient,
    private val mangaDao: MangaDao,
    private val classifier: ContentRatingClassifier,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun load(page: Int, adult: Boolean = false): CatalogPage = withContext(ioDispatcher) {
        require(page >= 1) { "Page numbers are 1-based" }
        val window = PageWindow(page)
        val fetched = coroutineScope {
            val aniList = async {
                attempt {
                    aniListClient.discover(window.page, window.pageSize, includeAdult = adult)
                        .let { PageSlice(it.records, it.hasNextPage) }
                }
            }
            val mangaDex = async {
                attempt {
                    mangaDexClient.discover(window.page, window.pageSize, includeAdult = adult)
                }
            }
            // Western fallbacks are public-shelf only; the +18 Vault has its own feed.
            // A skipped provider is `null` (neutral), not an empty success, so an
            // offline page can be distinguished from a genuinely exhausted feed.
            val mangaDexWestern = async {
                if (adult) null
                else attempt { mangaDexClient.western(window.page, window.pageSize) }
            }
            val comicVine = async {
                if (adult || !comicVineClient.isAvailable) null
                else attempt { comicVineClient.discover(window.page, window.pageSize) }
            }
            val openLibrary = async {
                if (adult) null
                else attempt { openLibraryClient.discover(window.page, window.pageSize) }
            }
            listOf(aniList.await(), mangaDex.await(), mangaDexWestern.await(), comicVine.await(), openLibrary.await())
        }

        // Every active provider failed: surface an error without advancing the
        // cursor, so the footer can offer a retry instead of a false "the end".
        if (fetched.all { it == null }) {
            throw IllegalStateException("Discovery providers are unreachable")
        }

        val records = fetched.flatMap { it?.items.orEmpty() }
            .distinctBy { "${it.sourceId}:${it.remoteId}" }
        val persisted = mutableListOf<Manga>()
        val seenIds = HashSet<String>()
        for (record in records) {
            val manga = CatalogRecordMapper.toMangaOrNull(record, classifier, allowExplicit = adult) ?: continue
            if (adult && manga.contentRating != ContentRating.EXPLICIT) continue
            val result = try {
                CatalogMerger.upsertMergedAndReturn(mangaDao, manga)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // One un-writable row must not strand the rest of the page.
                continue
            }
            val stored = result.entity.toModel()
            if (adult && stored.contentRating != ContentRating.EXPLICIT) continue
            if (seenIds.add(stored.id)) persisted += stored
        }

        val providerHasMore = fetched.any { it?.hasMore == true }
        CatalogPage(
            page = window.page,
            items = persisted,
            hasMore = providerHasMore && page < MAX_PAGE,
        )
    }

    private suspend fun <T> attempt(block: suspend () -> PageSlice<T>): PageSlice<T>? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Best-effort by design: the next page may still succeed.
        null
    }

    private companion object {
        /** 200 windows x 25 records = 5,000 series per provider before the feed rests. */
        const val MAX_PAGE = 200
    }
}
