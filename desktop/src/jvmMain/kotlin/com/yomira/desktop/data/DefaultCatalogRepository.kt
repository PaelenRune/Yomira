package com.yomira.desktop.data

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.network.AniListClient
import com.yomira.core.network.BakaUpdatesClient
import com.yomira.core.network.ComicVineClient
import com.yomira.core.network.KitsuClient
import com.yomira.core.network.MangaDexClient
import com.yomira.core.network.MetadataClientException
import com.yomira.core.network.MetadataProvider
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.NetworkConfiguration
import com.yomira.core.network.OpenLibraryClient
import com.yomira.core.network.RemoteMetadata
import com.yomira.data.metadata.MetadataCache
import com.yomira.data.metadata.WaterfallMetadataAggregator
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.normalizeTitle
import com.yomira.desktop.model.toCatalogItem
import com.yomira.desktop.state.PageCursor
import com.yomira.domain.classifier.ClassificationInput
import com.yomira.domain.classifier.ContentRatingClassifier
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

/**
 * Desktop implementation of the catalogue boundary.
 *
 * Browse uses the MangaDex discovery feed because it is the one shared source
 * whose records can also be read page-by-page on desktop; AniList is the
 * fallback when MangaDex is unavailable. Search intentionally fans out to every
 * provider in [WaterfallMetadataAggregator]'s canonical order, so the desktop
 * app sees exactly the same catalogue as the Android app.
 *
 * Adult records are hidden by default. The shared classifier decides what is
 * explicit, not a title keyword check in the UI.
 */
class DefaultCatalogRepository(
    httpClient: OkHttpClient,
    private val config: NetworkConfiguration = NetworkConfiguration(),
    private val classifier: ContentRatingClassifier = ContentRatingClassifier(),
    cache: MetadataCache = InMemoryMetadataCache(),
    private val aniList: AniListClient = AniListClient(httpClient, config = config),
    private val mangaDex: MangaDexClient = MangaDexClient(httpClient, config = config),
    providers: List<MetadataProvider> = defaultProviders(httpClient, config),
) : CatalogRepository {

    private val providers = providers.sortedBy { provider ->
        SOURCE_ORDER.indexOf(provider.sourceId).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private val aggregator = WaterfallMetadataAggregator(
        providers = this.providers,
        cache = cache,
        classifier = classifier,
    )

    override suspend fun browse(cursor: PageCursor, pageSize: Int): CatalogPage {
        require(pageSize in 1..CatalogRepository.MAX_PAGE_SIZE) {
            "Page size must be between 1 and ${CatalogRepository.MAX_PAGE_SIZE}"
        }
        return try {
            val slice = mangaDex.discover(page = cursor.page, perPage = pageSize, includeAdult = false)
            if (slice.items.isNotEmpty()) {
                CatalogPage(slice.items.map(RemoteMetadata::toCatalogItem).filterNot(::isExplicitItem), slice.hasMore)
            } else {
                browseAniList(cursor, pageSize)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: MetadataClientException) {
            browseAniList(cursor, pageSize)
        } catch (_: Exception) {
            browseAniList(cursor, pageSize)
        }
    }

    private suspend fun browseAniList(cursor: PageCursor, pageSize: Int): CatalogPage {
        val page = aniList.discover(page = cursor.page, perPage = pageSize, includeAdult = false)
        return CatalogPage(page.records.map(RemoteMetadata::toCatalogItem), page.hasNextPage)
    }

    override suspend fun search(query: String): List<CatalogItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val perSource = coroutineScope {
            providers.map { provider ->
                async { guarded(provider.sourceId) { provider.search(trimmed, SEARCH_LIMIT_PER_SOURCE) }.orEmpty() }
            }.awaitAll()
        }
        val records = perSource.flatten()
            .filter { it.title.isNotBlank() }
            .filterNot(::isExplicitRecord)
        return deduplicate(records)
            .asSequence()
            .map(RemoteMetadata::toCatalogItem)
            .filterNot(::isExplicitItem)
            .take(CatalogRepository.SEARCH_RESULT_LIMIT)
            .toList()
    }

    override suspend fun detail(item: CatalogItem): Manga? {
        val aggregated = guarded("metadata-waterfall") { aggregator.aggregate(item.title) }
        if (aggregated != null) return aggregated
        // A direct lookup keeps the detail page useful when the title no longer
        // matches a search hit but the provider still knows the record.
        val direct = guarded(item.sourceId) {
            when (item.sourceId) {
                MetadataSources.MANGADEX -> mangaDex.findById(item.remoteId)
                MetadataSources.ANILIST -> aniList.findById(item.remoteId)
                else -> null
            }
        }
        return direct?.let(RemoteMetadata::toCatalogItem)?.let { fallback ->
            Manga(
                id = fallback.id,
                title = fallback.title,
                coverUrl = fallback.coverUrl,
                bannerUrl = fallback.bannerUrl,
                author = fallback.author,
                synopsis = fallback.synopsis,
                score = fallback.score,
                status = fallback.status,
                genres = fallback.genres,
                contentRating = fallback.contentRating,
                sourceId = fallback.sourceId,
            )
        }
    }

    /**
     * Collapses the same series returned by several providers. Provider order is
     * the waterfall order, so the first record owns identity and later records
     * only fill holes — exactly like the shared aggregator.
     *
     * Matching compares every known title (canonical, English and alt titles)
     * because the first provider may only know the romaji name while another
     * knows the English one. The search result is small (six providers × eight
     * hits), so the pairwise scan is cheaper than maintaining a title index.
     */
    private fun deduplicate(records: List<RemoteMetadata>): List<RemoteMetadata> {
        val merged = mutableListOf<RemoteMetadata>()
        for (record in records) {
            val titles = record.allTitles().map(::normalizeTitle).filter { it.isNotEmpty() }.toSet()
            val index = merged.indexOfFirst { existing ->
                existing.allTitles().asSequence().map(::normalizeTitle).any { it in titles }
            }
            if (index >= 0) {
                merged[index] = merge(merged[index], record)
            } else {
                merged += record
            }
        }
        return merged
    }

    private fun merge(first: RemoteMetadata, second: RemoteMetadata): RemoteMetadata = first.copy(
        englishTitle = first.englishTitle ?: second.englishTitle,
        author = first.author ?: second.author,
        artist = first.artist ?: second.artist,
        description = first.description ?: second.description,
        coverUrl = first.coverUrl ?: second.coverUrl,
        bannerUrl = first.bannerUrl ?: second.bannerUrl,
        score = first.score ?: second.score,
        status = if (first.status != MangaStatus.UNKNOWN) first.status else second.status,
        genres = (first.genres + second.genres).distinct(),
        tags = (first.tags + second.tags).distinct(),
        contentRating = first.contentRating ?: second.contentRating,
        year = first.year ?: second.year,
        sourceUrl = first.sourceUrl ?: second.sourceUrl,
    )

    private fun isExplicitRecord(record: RemoteMetadata): Boolean {
        // Hard evidence first: never even classify pornographic records.
        if (record.contentRating == com.yomira.core.model.ExternalRating.PORNOGRAPHIC) return true
        return classifier.classify(
            ClassificationInput(
                title = record.englishTitle ?: record.title,
                altTitles = record.allTitles(),
                genres = record.genres,
                tags = record.tags,
                externalRatings = listOfNotNull(record.contentRating),
                isOfficialPublication = record.isOfficialPublication,
                publicationType = record.publicationType,
            ),
        ) == ContentRating.EXPLICIT
    }

    private fun isExplicitItem(item: CatalogItem): Boolean = item.contentRating == ContentRating.EXPLICIT

    /** One provider failure must never cancel the whole search. */
    private suspend fun <T> guarded(@Suppress("UNUSED_PARAMETER") source: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val SEARCH_LIMIT_PER_SOURCE = 8
        private val SOURCE_ORDER = listOf(
            MetadataSources.BAKA_UPDATES,
            MetadataSources.ANILIST,
            MetadataSources.MANGADEX,
            MetadataSources.KITSU,
            MetadataSources.COMICVINE,
            MetadataSources.OPENLIBRARY,
        )

        /** Same provider set and construction as the Android DI layer. */
        fun defaultProviders(httpClient: OkHttpClient, config: NetworkConfiguration): List<MetadataProvider> = listOf(
            BakaUpdatesClient(httpClient, config = config),
            AniListClient(httpClient, config = config),
            MangaDexClient(httpClient, config = config),
            KitsuClient(httpClient, config = config),
            ComicVineClient(httpClient, config = config),
            OpenLibraryClient(httpClient, config = config),
        )
    }
}

/**
 * Process-local cache for the shared waterfall. The desktop app keeps no
 * database; 24-hour entries mirror the Android TTL and disappear on exit.
 */
class InMemoryMetadataCache(
    private val maxEntries: Int = 512,
) : MetadataCache {
    init {
        require(maxEntries > 0) { "Cache size must be positive" }
    }

    private data class Entry(val payload: String, val expiresAtMillis: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    override suspend fun read(key: String, nowMillis: Long): String? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMillis <= nowMillis) {
            entries.remove(key, entry)
            return null
        }
        return entry.payload
    }

    override suspend fun write(key: String, payload: String, nowMillis: Long, expiresAtMillis: Long) {
        require(expiresAtMillis > nowMillis) { "Cache entry must expire after it is written" }
        if (entries.size >= maxEntries) evict(nowMillis)
        entries[key] = Entry(payload, expiresAtMillis)
    }

    private fun evict(nowMillis: Long) {
        entries.entries.removeIf { it.value.expiresAtMillis <= nowMillis }
        if (entries.size < maxEntries) return
        entries.entries
            .sortedBy { it.value.expiresAtMillis }
            .take(entries.size - maxEntries + 1)
            .forEach { entries.remove(it.key, it.value) }
    }
}
