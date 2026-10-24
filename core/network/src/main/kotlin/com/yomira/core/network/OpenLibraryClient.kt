package com.yomira.core.network

import com.yomira.core.model.PageSlice
import com.yomira.core.model.PageWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenLibrary (Internet Archive) search client.
 *
 * It is the key-less western-comics fallback: when ComicVine has no API key or
 * is rate-limited, graphic-novel records still reach the spotlight. OpenLibrary
 * exposes works, not chapters, so this provider only contributes metadata.
 */
class OpenLibraryClient(
    private val httpClient: OkHttpClient,
    private val json: Json = NetworkJson.instance,
    private val config: NetworkConfiguration = NetworkConfiguration(),
) : MetadataProvider {
    override val sourceId: String = MetadataSources.OPENLIBRARY

    override suspend fun search(query: String, limit: Int): List<RemoteMetadata> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        fetch(query = trimmed, page = 1, limit = limit.coerceIn(1, 50)).items
    }

    /**
     * One page of the graphic-novel shelf. [PageWindow] supplies the 1-based
     * page index OpenLibrary expects (`page=2` is the second window, not offset).
     */
    suspend fun discover(
        page: Int = 1,
        perPage: Int = PageWindow.DEFAULT_PAGE_SIZE,
    ): PageSlice<RemoteMetadata> = withContext(Dispatchers.IO) {
        val window = PageWindow(page, perPage.coerceIn(1, PageWindow.MAX_PAGE_SIZE))
        fetch(query = GRAPHIC_NOVEL_QUERY, page = window.page, limit = window.pageSize)
    }

    private fun fetch(query: String, page: Int, limit: Int): PageSlice<RemoteMetadata> {
        val url = "${config.openLibraryBaseUrl.trimEnd('/')}/search.json".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("page", page.toString())
            ?.addQueryParameter("limit", limit.toString())
            ?.addQueryParameter("fields", FIELDS)
            ?.addQueryParameter("sort", "new")
            ?.build()
            ?: return PageSlice(emptyList(), hasMore = false)
        val request = Request.Builder().url(url).commonHeaders(config.userAgent).build()
        val payload = httpClient.executeChecked(request, sourceId).use { response ->
            response.decodeJson<SearchResponse>(json, sourceId)
        }
        val items = payload.docs.mapNotNull { it.toMetadata() }
        val hasMore = payload.numFound?.let { page * limit < it } ?: (payload.docs.size >= limit)
        return PageSlice(items, hasMore)
    }

    private fun OpenLibraryDoc.toMetadata(): RemoteMetadata? {
        val title = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val key = key?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val id = key.removePrefix("/works/")
        if (id.isEmpty()) return null
        val subjects = subjects.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return RemoteMetadata(
            sourceId = sourceId,
            remoteId = id,
            title = title,
            author = authorNames.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                .distinct().joinToString(", ").ifBlank { null },
            coverUrl = coverId?.takeIf { it > 0 }?.let { "${config.openLibraryCoversUrl.trimEnd('/')}/b/id/$it-L.jpg" },
            publisher = publishers.orEmpty().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            year = firstPublishYear?.takeIf { it in 1800..2200 },
            genres = subjects.take(MAX_SUBJECTS),
            tags = subjects.take(MAX_SUBJECTS),
            publicationType = "Comic",
            isOfficialPublication = true,
            sourceUrl = "https://openlibrary.org$key",
        )
    }

    @Serializable
    private data class SearchResponse(
        @SerialName("numFound") val numFound: Int? = null,
        val docs: List<OpenLibraryDoc> = emptyList(),
    )

    @Serializable
    private data class OpenLibraryDoc(
        val key: String? = null,
        val title: String? = null,
        @SerialName("author_name") val authorNames: List<String>? = null,
        @SerialName("first_publish_year") val firstPublishYear: Int? = null,
        @SerialName("cover_i") val coverId: Int? = null,
        val subject: List<String>? = null,
        val publisher: List<String>? = null,
    )

    // `subject` is the OpenLibrary field name; the domain-friendly alias keeps the mapper readable.
    private val OpenLibraryDoc.subjects: List<String>? get() = subject
    private val OpenLibraryDoc.publishers: List<String>? get() = publisher

    private companion object {
        const val FIELDS = "key,title,author_name,first_publish_year,cover_i,subject,publisher"
        const val MAX_SUBJECTS = 12
        const val GRAPHIC_NOVEL_QUERY = "subject:\"Comic books, strips, etc.\""
    }
}
