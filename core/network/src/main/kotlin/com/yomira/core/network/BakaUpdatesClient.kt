package com.yomira.core.network

import com.yomira.core.model.MangaStatus
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * MangaUpdates (Baka-Updates) client.
 *
 * The JSON API is the canonical spine of the waterfall: it carries the most
 * complete tag set, author/artist split, serialisation status and the
 * "official publication" hint used by the content classifier. The public HTML
 * catalogue is a degraded fallback when the API is unavailable.
 */
class BakaUpdatesClient(
    private val httpClient: OkHttpClient,
    private val json: Json = NetworkJson.instance,
    private val config: NetworkConfiguration = NetworkConfiguration(),
) : MetadataProvider {

    override val sourceId: String = MetadataSources.BAKA_UPDATES

    override suspend fun search(query: String, limit: Int): List<RemoteMetadata> =
        withContext(Dispatchers.IO) {
            val apiResults = try {
                searchApi(query, limit)
            } catch (error: MetadataClientException) {
                emptyList()
            }
            if (apiResults.isNotEmpty()) apiResults else searchHtml(query, limit)
        }

    override suspend fun findById(remoteId: String): RemoteMetadata? = withContext(Dispatchers.IO) {
        val apiResult = try {
            val request = Request.Builder()
                .url("${config.mangaUpdatesBaseUrl}/v1/series/$remoteId")
                .commonHeaders(config.userAgent)
                .get()
                .build()
            httpClient.executeChecked(request, sourceId).use { response ->
                response.decodeJson<BakaRecord>(json, sourceId)
            }.toMetadata()
        } catch (error: MetadataClientException) {
            null
        }
        apiResult ?: fetchHtmlDetail("${config.mangaUpdatesWebUrl}/series.html?id=$remoteId")
    }

    private fun searchApi(query: String, limit: Int): List<RemoteMetadata> {
        val payload = """
            {"search":${jsonString(query)},"stype":"title","perpage":${limit.coerceIn(1, 20)}}
        """.trimIndent()
        val request = Request.Builder()
            .url("${config.mangaUpdatesBaseUrl}/v1/series/search")
            .commonHeaders(config.userAgent)
            .post(payload.asJsonBody())
            .build()
        return httpClient.executeChecked(request, sourceId).use { response ->
            response.decodeJson<BakaSearchResponse>(json, sourceId)
        }.results.mapNotNull { it.record?.toMetadata() }
    }

    private fun searchHtml(query: String, limit: Int): List<RemoteMetadata> {
        val url = "${config.mangaUpdatesWebUrl}/series.html".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("search", query)
            ?.build()
            ?: return emptyList()
        val request = Request.Builder()
            .url(url)
            .commonHeaders(config.userAgent)
            .get()
            .build()
        val html = try {
            httpClient.executeChecked(request, sourceId).use { it.body?.string().orEmpty() }
        } catch (error: MetadataClientException) {
            return emptyList()
        }
        return parseHtmlSearch(html, config.mangaUpdatesWebUrl, limit)
    }

    private fun fetchHtmlDetail(url: String): RemoteMetadata? {
        val request = Request.Builder()
            .url(url)
            .commonHeaders(config.userAgent)
            .get()
            .build()
        return try {
            val html = httpClient.executeChecked(request, sourceId).use { it.body?.string().orEmpty() }
            parseHtmlDetail(html, url, config.mangaUpdatesWebUrl)
        } catch (error: IOException) {
            null
        }
    }

    /** Visible for tests; tolerant of both legacy and current catalogue markup. */
    internal fun parseHtmlSearch(html: String, baseUrl: String, limit: Int): List<RemoteMetadata> {
        val document = Jsoup.parse(html, baseUrl)
        val seen = mutableSetOf<String>()
        return document.select("a[href*=/series/], a[href*=series.html?id=]")
            .asSequence()
            .filterNot { it.attr("href").contains("genre=") }
            .mapNotNull { anchor ->
                val href = anchor.attr("abs:href").ifBlank { anchor.attr("href") }
                if (href.contains("series.html?search=")) return@mapNotNull null
                val remoteId = extractRemoteId(href) ?: return@mapNotNull null
                val title = anchor.text().trim()
                if (title.length < 2 || !seen.add(remoteId)) return@mapNotNull null
                val row = anchor.parents().firstOrNull { it.selectFirst("img") != null }
                val image = row?.selectFirst("img")?.absUrl("src")?.ifBlank { null }
                    ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
                RemoteMetadata(
                    sourceId = sourceId,
                    remoteId = remoteId,
                    title = title,
                    coverUrl = image,
                    sourceUrl = href,
                )
            }
            .take(limit)
            .toList()
    }

    /** Visible for tests. */
    internal fun parseHtmlDetail(html: String, pageUrl: String, baseUrl: String): RemoteMetadata? {
        val document = Jsoup.parse(html, baseUrl)
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: document.title().substringBefore(" - ").trim()
        if (title.isBlank()) return null
        val remoteId = extractRemoteId(pageUrl) ?: return null
        val cover = document.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
            ?: document.selectFirst("img[src*=uploads]")?.absUrl("src")?.ifBlank { null }
        val description = document.selectFirst("meta[name=description]")?.attr("content")
            ?: document.selectFirst("div#description")?.text()
        val genres = document.select("a[href*=genre=]").map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val statusText = document.selectFirst("div[class*=status]")?.text()
            ?: document.selectFirst("li:contains(Status)")?.text()
        return RemoteMetadata(
            sourceId = sourceId,
            remoteId = remoteId,
            title = title,
            description = cleanText(description),
            coverUrl = cover,
            status = parseStatus(statusText),
            genres = genres,
            tags = genres,
            primaryGenre = genres.firstOrNull(),
            publicationType = "Manga",
            sourceUrl = pageUrl,
        )
    }

    private fun BakaRecord.toMetadata(): RemoteMetadata? {
        val canonicalTitle = title?.trim().orEmpty()
        if (canonicalTitle.isEmpty()) return null
        val seriesId = series_id?.toString() ?: extractRemoteId(url.orEmpty()) ?: return null
        val genreNames = genres.mapNotNull { it.genre?.trim()?.ifEmpty { null } }.distinct()
        val authorNames = authors.filter { it.type.equals("Author", true) }
            .mapNotNull { it.name?.trim() }
            .distinct()
        val artistNames = authors.filter { it.type.equals("Artist", true) }
            .mapNotNull { it.name?.trim() }
            .distinct()
        val publicationNames = publications.mapNotNull { it.publication_name?.trim() }
            .filter { it.isNotEmpty() }
        val publisherNames = publishers.mapNotNull { it.publisher_name?.trim() }
            .filter { it.isNotEmpty() }
        val associatedTitles = associated.mapNotNull { it.title?.trim() }
            .filter { it.isNotEmpty() && !it.equals(canonicalTitle, true) }
        val isDoujinshi = type.equals("Doujinshi", true)
        val official = when {
            isDoujinshi -> false
            publicationNames.isNotEmpty() -> true
            licensed == true -> true
            else -> null
        }
        return RemoteMetadata(
            sourceId = sourceId,
            remoteId = seriesId,
            title = canonicalTitle,
            altTitles = mapOf("associated" to associatedTitles),
            author = authorNames.joinToString(", ").ifBlank { null },
            artist = artistNames.joinToString(", ").ifBlank { null },
            description = cleanText(description),
            coverUrl = image?.url?.original?.ifBlank { null },
            score = bayesian_rating?.takeIf { it.isFinite() }?.coerceIn(0.0, 10.0),
            status = parseStatus(status),
            genres = genreNames,
            tags = genreNames,
            primaryGenre = genreNames.firstOrNull(),
            publicationType = type?.trim()?.ifEmpty { null },
            publisher = publisherNames.joinToString(", ").ifBlank { null },
            isOfficialPublication = official,
            year = year?.trim()?.toIntOrNull()?.takeIf { it in 1800..2200 },
            sourceUrl = url?.ifBlank { null },
        )
    }

    private fun parseStatus(raw: String?): MangaStatus = when {
        raw == null -> MangaStatus.UNKNOWN
        raw.contains("ongoing", ignoreCase = true) || raw.contains("release", ignoreCase = true) ->
            MangaStatus.ONGOING
        raw.contains("complete", ignoreCase = true) -> MangaStatus.COMPLETED
        raw.contains("hiatus", ignoreCase = true) -> MangaStatus.HIATUS
        raw.contains("cancel", ignoreCase = true) || raw.contains("discontinu", ignoreCase = true) ->
            MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    private fun extractRemoteId(url: String): String? {
        Regex("/series/([A-Za-z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("[?&]id=([A-Za-z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private fun jsonString(value: String): String =
        kotlinx.serialization.json.JsonPrimitive(value).toString()

    @Serializable
    private data class BakaSearchResponse(
        val results: List<BakaSearchResult> = emptyList(),
    )

    @Serializable
    private data class BakaSearchResult(val record: BakaRecord? = null)

    @Serializable
    private data class BakaRecord(
        val series_id: Long? = null,
        val title: String? = null,
        val url: String? = null,
        val description: String? = null,
        val image: BakaImage? = null,
        val type: String? = null,
        val year: String? = null,
        @SerialName("bayesian_rating") val bayesian_rating: Double? = null,
        @SerialName("rating_votes") val rating_votes: Int? = null,
        val genres: List<BakaGenre> = emptyList(),
        @SerialName("latest_chapter") val latest_chapter: Int? = null,
        val status: String? = null,
        val licensed: Boolean? = null,
        val completed: Boolean? = null,
        val authors: List<BakaAuthor> = emptyList(),
        val publishers: List<BakaPublisher> = emptyList(),
        val publications: List<BakaPublication> = emptyList(),
        val associated: List<BakaAssociation> = emptyList(),
    )

    @Serializable
    private data class BakaImage(val url: BakaImageUrl? = null)

    @Serializable
    private data class BakaImageUrl(
        val original: String? = null,
        val thumb: String? = null,
    )

    @Serializable
    private data class BakaGenre(val genre: String? = null)

    @Serializable
    private data class BakaAuthor(
        val name: String? = null,
        val type: String? = null,
    )

    @Serializable
    private data class BakaPublisher(
        @SerialName("publisher_name") val publisher_name: String? = null,
    )

    @Serializable
    private data class BakaPublication(
        @SerialName("publication_name") val publication_name: String? = null,
    )

    @Serializable
    private data class BakaAssociation(
        val title: String? = null,
        @SerialName("association_type") val association_type: String? = null,
    )
}
