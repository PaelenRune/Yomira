package com.yomira.core.network

import com.yomira.core.model.ExternalRating
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.PageWindow
import com.yomira.core.model.PageSlice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * MangaDex v5 REST client.
 *
 * MangaDex is the richest source for multilingual titles and the authoritative
 * provider of the four-tier `contentRating` evidence (safe, suggestive,
 * erotica, pornographic) consumed by [com.yomira.domain.classifier].
 *
 * All four ratings are requested explicitly: the API would otherwise hide
 * explicit entries, and the classifier must be able to see and isolate them.
 */
class MangaDexClient(
    private val httpClient: OkHttpClient,
    private val json: Json = NetworkJson.instance,
    private val config: NetworkConfiguration = NetworkConfiguration(),
) : MetadataProvider {

    override val sourceId: String = MetadataSources.MANGADEX

    override suspend fun search(query: String, limit: Int): List<RemoteMetadata> =
        withContext(Dispatchers.IO) {
            val url = "${config.mangaDexBaseUrl}/manga".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("title", query)
                ?.addQueryParameter("limit", limit.coerceIn(1, 20).toString())
                ?.addQueryParameter("order[relevance]", "desc")
                ?.addAll("includes[]", LIST_INCLUDES)
                ?.addAll("contentRating[]", ALL_RATINGS)
                ?.build()
                ?: return@withContext emptyList()
            val request = Request.Builder()
                .url(url)
                .commonHeaders(config.userAgent)
                .get()
                .build()
            val payload = httpClient.executeChecked(request, sourceId).use { response ->
                response.decodeJson<MangaDexListResponse>(json, sourceId)
            }
            val records = payload.data
            val statistics = fetchStatistics(records.mapNotNull { it.id })
            records.mapNotNull { it.toMetadata(statistics[it.id]) }
        }

    override suspend fun findById(remoteId: String): RemoteMetadata? = withContext(Dispatchers.IO) {
        val url = "${config.mangaDexBaseUrl}/manga/$remoteId".toHttpUrlOrNull()?.newBuilder()
            ?.addAll("includes[]", LIST_INCLUDES)
            ?.build()
            ?: return@withContext null
        val request = Request.Builder()
            .url(url)
            .commonHeaders(config.userAgent)
            .get()
            .build()
        val record = httpClient.executeChecked(request, sourceId).use { response ->
            response.decodeJson<MangaDexSingleResponse>(json, sourceId)
        }.data ?: return@withContext null
        record.toMetadata(fetchStatistics(listOfNotNull(record.id))[record.id])
    }

    /**
     * Fallback trending feed for first-launch cache warming. Only the two
     * non-explicit rating buckets are requested so nothing adult reaches the
     * public home feed by accident.
     */
    suspend fun trending(limit: Int = 20): List<RemoteMetadata> =
        discover(page = 1, perPage = limit, includeAdult = false).items

    /** One page of the infinite discovery feed (`offset += perPage` per call). */
    suspend fun discover(
        page: Int = 1,
        perPage: Int = PageWindow.DEFAULT_PAGE_SIZE,
        includeAdult: Boolean = false,
    ): PageSlice<RemoteMetadata> = withContext(Dispatchers.IO) {
        val window = PageWindow(page, perPage.coerceIn(1, PageWindow.MAX_PAGE_SIZE))
        fetchPage(
            window = window,
            ratings = if (includeAdult) ADULT_RATINGS else PUBLIC_RATINGS,
            order = "followedCount",
            originalLanguage = null,
        )
    }

    /**
     * English-original records only. This is the western-comics fallback used by
     * the spotlight and by the public infinite feed; `originalLanguage=en`
     * excludes Japanese/Korean/Chinese originals regardless of translation.
     */
    suspend fun western(
        page: Int = 1,
        perPage: Int = PageWindow.DEFAULT_PAGE_SIZE,
    ): PageSlice<RemoteMetadata> = withContext(Dispatchers.IO) {
        val window = PageWindow(page, perPage.coerceIn(1, PageWindow.MAX_PAGE_SIZE))
        fetchPage(
            window = window,
            ratings = PUBLIC_RATINGS,
            order = "followedCount",
            originalLanguage = "en",
        )
    }

    private suspend fun fetchPage(
        window: PageWindow,
        ratings: List<String>,
        order: String,
        originalLanguage: String?,
    ): PageSlice<RemoteMetadata> {
        val url = "${config.mangaDexBaseUrl}/manga".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("limit", window.pageSize.toString())
            ?.addQueryParameter("offset", window.offset.toString())
            ?.addQueryParameter("order[$order]", "desc")
            ?.addAll("includes[]", LIST_INCLUDES)
            ?.addAll("contentRating[]", ratings)
            ?.apply { originalLanguage?.let { addQueryParameter("originalLanguage[]", it) } }
            ?.build()
            ?: return PageSlice(emptyList(), hasMore = false)
        val request = Request.Builder()
            .url(url)
            .commonHeaders(config.userAgent)
            .get()
            .build()
        val payload = httpClient.executeChecked(request, sourceId).use { response ->
            response.decodeJson<MangaDexListResponse>(json, sourceId)
        }
        val records = payload.data
        val statistics = fetchStatistics(records.mapNotNull { it.id })
        val items = records.mapNotNull { it.toMetadata(statistics[it.id]) }
        val received = payload.offset ?: window.offset
        val hasMore = payload.total?.let { received + records.size < it }
            ?: (records.size >= window.pageSize)
        return PageSlice(items, hasMore)
    }

    /** Statistics are optional: artwork, titles and tags survive a statistics outage. */
    private fun fetchStatistics(ids: List<String>): Map<String, MangaDexStatistics> {
        if (ids.isEmpty()) return emptyMap()
        val url = "${config.mangaDexBaseUrl}/statistics/manga".toHttpUrlOrNull()?.newBuilder()
            ?.addAll("manga[]", ids)
            ?.build()
            ?: return emptyMap()
        val request = Request.Builder()
            .url(url)
            .commonHeaders(config.userAgent)
            .get()
            .build()
        return try {
            httpClient.executeChecked(request, sourceId).use { response ->
                response.decodeJson<MangaDexStatisticsResponse>(json, sourceId)
            }.statistics.orEmpty()
        } catch (error: MetadataClientException) {
            emptyMap()
        }
    }

    private fun MangaDexManga.toMetadata(statistics: MangaDexStatistics?): RemoteMetadata? {
        val mangaId = id ?: return null
        val attributes = attributes ?: return null
        val canonical = attributes.title.pickTitle() ?: return null
        val titleGroups = buildMap {
            attributes.title.forEach { (locale, value) ->
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) put(locale, listOf(trimmed))
            }
            attributes.altTitles.forEach { entry ->
                entry.forEach { (locale, value) ->
                    val trimmed = value.trim()
                    if (trimmed.isEmpty()) return@forEach
                    put(locale, (get(locale).orEmpty() + trimmed).distinct())
                }
            }
        }
        val tagNames = attributes.tags.mapNotNull { it.attributes?.name?.pickTitle()?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val genreNames = attributes.tags
            .filter { it.attributes?.group?.equals("genre", ignoreCase = true) == true }
            .mapNotNull { it.attributes?.name?.pickTitle()?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val coverFile = relationships.firstOrNull { it.type == "cover_art" }
            ?.attributes?.fileName
            ?.trim()
            ?.ifEmpty { null }
        val authorNames = relationships.filter { it.type == "author" }
            .mapNotNull { it.attributes?.name?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val artistNames = relationships.filter { it.type == "artist" }
            .mapNotNull { it.attributes?.name?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return RemoteMetadata(
            sourceId = sourceId,
            remoteId = mangaId,
            title = canonical,
            altTitles = titleGroups,
            // MangaDex keeps every locale in `title`/`altTitles`; the English key
            // ("en") becomes the display-title candidate for the shelf, e.g.
            // "Shingeki no Kyojin" is surfaced as "Attack on Titan".
            englishTitle = titleGroups["en"]?.firstOrNull(),
            author = authorNames.joinToString(", ").ifBlank { null },
            artist = artistNames.joinToString(", ").ifBlank { null },
            description = cleanText(attributes.description.pickTitle()),
            coverUrl = coverFile?.let { "${config.mangaDexUploadsUrl}/covers/$mangaId/$it.512.jpg" },
            score = statistics?.rating?.average?.takeIf { it.isFinite() && it > 0.0 }
                ?.coerceIn(0.0, 10.0),
            popularity = statistics?.follows?.takeIf { it >= 0 },
            status = parseStatus(attributes.status),
            genres = genreNames,
            tags = tagNames,
            primaryGenre = genreNames.firstOrNull(),
            contentRating = ExternalRating.fromApiValue(attributes.contentRating),
            publicationType = publicationType(attributes.originalLanguage),
            year = attributes.year,
            totalIssues = attributes.lastVolume?.toIntOrNull(),
            sourceUrl = "https://mangadex.org/title/$mangaId",
        )
    }

    private fun Map<String, String>.pickTitle(): String? {
        if (isEmpty()) return null
        return this["en"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: this["ja-ro"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: this["ja"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: values.firstNotNullOfOrNull { it.trim().takeIf(String::isNotEmpty) }
    }

    private fun publicationType(language: String?): String? = when (language?.lowercase()) {
        "ja" -> "Manga"
        "ko" -> "Manhwa"
        "zh", "zh-hk" -> "Manhua"
        "en" -> "Comic"
        null, "" -> null
        else -> "Manga"
    }

    private fun parseStatus(raw: String?): MangaStatus = when (raw?.lowercase()) {
        "ongoing" -> MangaStatus.ONGOING
        "completed" -> MangaStatus.COMPLETED
        "hiatus" -> MangaStatus.HIATUS
        "cancelled" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    @Serializable
    private data class MangaDexListResponse(
        val result: String? = null,
        val data: List<MangaDexManga> = emptyList(),
        val limit: Int? = null,
        val offset: Int? = null,
        val total: Int? = null,
    )

    @Serializable
    private data class MangaDexSingleResponse(
        val result: String? = null,
        val data: MangaDexManga? = null,
    )

    @Serializable
    private data class MangaDexStatisticsResponse(
        val result: String? = null,
        val statistics: Map<String, MangaDexStatistics>? = null,
    )

    @Serializable
    private data class MangaDexStatistics(
        val comments: MangaDexCount? = null,
        val follows: Long? = null,
        val rating: MangaDexRating? = null,
    )

    @Serializable
    private data class MangaDexCount(val total: Long? = null)

    @Serializable
    private data class MangaDexRating(
        val average: Double? = null,
        val bayesian: Double? = null,
    )

    @Serializable
    private data class MangaDexManga(
        val id: String? = null,
        val type: String? = null,
        val attributes: MangaDexAttributes? = null,
        val relationships: List<MangaDexRelationship> = emptyList(),
    )

    @Serializable
    private data class MangaDexAttributes(
        val title: Map<String, String> = emptyMap(),
        val altTitles: List<Map<String, String>> = emptyList(),
        val description: Map<String, String> = emptyMap(),
        val status: String? = null,
        val year: Int? = null,
        @SerialName("contentRating") val contentRating: String? = null,
        val tags: List<MangaDexTag> = emptyList(),
        @SerialName("originalLanguage") val originalLanguage: String? = null,
        @SerialName("publicationDemographic") val publicationDemographic: String? = null,
        @SerialName("lastVolume") val lastVolume: String? = null,
        @SerialName("lastChapter") val lastChapter: String? = null,
    )

    @Serializable
    private data class MangaDexTag(val attributes: MangaDexTagAttributes? = null)

    @Serializable
    private data class MangaDexTagAttributes(
        val name: Map<String, String> = emptyMap(),
        val group: String? = null,
    )

    @Serializable
    private data class MangaDexRelationship(
        val id: String? = null,
        val type: String? = null,
        val attributes: MangaDexRelationshipAttributes? = null,
    )

    @Serializable
    private data class MangaDexRelationshipAttributes(
        val fileName: String? = null,
        val name: String? = null,
        val imageUrl: String? = null,
    )

    private companion object {
        val LIST_INCLUDES = listOf("cover_art", "author", "artist")
        val ALL_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")
        val PUBLIC_RATINGS = listOf("safe", "suggestive")
        val ADULT_RATINGS = listOf("erotica", "pornographic")
    }
}
