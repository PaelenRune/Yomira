package com.yomira.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Volume metadata only; the API key is supplied by the caller, never bundled. */
class ComicVineClient(
    private val httpClient: OkHttpClient,
    private val json: Json = NetworkJson.instance,
    private val config: NetworkConfiguration = NetworkConfiguration(),
) : MetadataProvider {
    override val sourceId = MetadataSources.COMICVINE
    override val isAvailable get() = config.comicVineApiKey.isNotBlank()

    override suspend fun search(query: String, limit: Int): List<RemoteMetadata> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext emptyList()
        val url = "${config.comicVineBaseUrl.trimEnd('/')}/search/".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", config.comicVineApiKey)
            .addQueryParameter("format", "json")
            .addQueryParameter("resources", "volume")
            .addQueryParameter("query", query)
            .addQueryParameter("limit", limit.coerceIn(1, 20).toString()).build()
        val request = Request.Builder().url(url).commonHeaders(config.userAgent).build()
        val response = httpClient.executeChecked(request, sourceId).use { it.decodeJson<SearchResponse>(json, sourceId) }
        if (response.status_code != 1) throw MetadataClientException(sourceId, message = "ComicVine rejected the request")
        response.results.mapNotNull { it.toMetadata() }
    }

    override suspend fun findById(remoteId: String): RemoteMetadata? = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext null
        require(remoteId.matches(Regex("(?:4050-)?[0-9]+"))) { "Invalid ComicVine volume ID" }
        val id = remoteId.removePrefix("4050-")
        val url = "${config.comicVineBaseUrl.trimEnd('/')}/volume/4050-$id/".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", config.comicVineApiKey).addQueryParameter("format", "json").build()
        val request = Request.Builder().url(url).commonHeaders(config.userAgent).build()
        val response = httpClient.executeChecked(request, sourceId).use { it.decodeJson<DetailResponse>(json, sourceId) }
        if (response.status_code != 1) throw MetadataClientException(sourceId, message = "ComicVine rejected the request")
        response.results?.toMetadata()
    }

    private fun Volume.toMetadata(): RemoteMetadata? {
        val title = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val id = id ?: return null
        return RemoteMetadata(
            sourceId = sourceId, remoteId = id.toString(), title = title,
            altTitles = mapOf("aliases" to aliases.orEmpty().lines().map(String::trim).filter(String::isNotBlank)),
            description = cleanText(description ?: deck), coverUrl = image?.original_url ?: image?.super_url,
            publicationType = "Comic", publisher = publisher?.name, isOfficialPublication = publisher?.name?.isNotBlank(),
            year = start_year?.toIntOrNull()?.takeIf { it in 1800..2200 }, totalIssues = count_of_issues,
            sourceUrl = site_detail_url,
        )
    }

    @Serializable private data class SearchResponse(val status_code: Int = 0, val results: List<Volume> = emptyList())
    @Serializable private data class DetailResponse(val status_code: Int = 0, val results: Volume? = null)
    @Serializable private data class Volume(
        val id: Long? = null, val name: String? = null, val aliases: String? = null,
        val description: String? = null, val deck: String? = null, val image: Image? = null,
        val publisher: Publisher? = null, val start_year: String? = null,
        val count_of_issues: Int? = null, val site_detail_url: String? = null,
    )
    @Serializable private data class Image(val original_url: String? = null, val super_url: String? = null)
    @Serializable private data class Publisher(val name: String? = null)
}
