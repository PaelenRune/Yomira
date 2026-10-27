package com.yomira.core.network

import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ExternalRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Kitsu REST client.
 *
 * Fills remaining metadata after AniList and MangaDex. Included category
 * resources are scoped to each manga relationship, not shared across results.
 */
class KitsuClient(
    private val httpClient: OkHttpClient,
    private val json: Json = NetworkJson.instance,
    private val config: NetworkConfiguration = NetworkConfiguration(),
) : MetadataProvider {

    override val sourceId: String = MetadataSources.KITSU

    override suspend fun search(query: String, limit: Int): List<RemoteMetadata> =
        withContext(Dispatchers.IO) {
            val url = "${config.kitsuBaseUrl}/manga".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("filter[text]", query)
                ?.addQueryParameter("page[limit]", limit.coerceIn(1, 20).toString())
                ?.addQueryParameter("include", "categories")
                ?.build()
                ?: return@withContext emptyList()
            val request = Request.Builder()
                .url(url)
                .commonHeaders(config.userAgent)
                .get()
                .build()
            val payload = httpClient.executeChecked(request, sourceId).use { response ->
                response.decodeJson<KitsuListResponse>(json, sourceId)
            }
            val categories = categoryMap(payload.included)
            payload.data.mapNotNull { it.toMetadata(it.categoryNames(categories)) }
        }

    override suspend fun findById(remoteId: String): RemoteMetadata? = withContext(Dispatchers.IO) {
        val url = "${config.kitsuBaseUrl}/manga/$remoteId".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("include", "categories")
            ?.build()
            ?: return@withContext null
        val request = Request.Builder()
            .url(url)
            .commonHeaders(config.userAgent)
            .get()
            .build()
        val payload = httpClient.executeChecked(request, sourceId).use { response ->
            response.decodeJson<KitsuSingleResponse>(json, sourceId)
        }
        val categories = categoryMap(payload.included)
        payload.data?.let { it.toMetadata(it.categoryNames(categories)) }
    }

    private fun categoryMap(included: List<KitsuResource>?) = included.orEmpty()
        .filter { it.type == "categories" }
        .mapNotNull { resource ->
            val id = resource.id ?: return@mapNotNull null
            resource.attributes?.title?.trim()?.takeIf(String::isNotBlank)?.let { id to it }
        }.toMap()

    private fun KitsuResource.categoryNames(categories: Map<String, String>) =
        relationships?.categories?.data.orEmpty().mapNotNull { categories[it.id] }.distinct()

    private fun KitsuResource.toMetadata(categories: List<String>): RemoteMetadata? {
        val attributes = attributes ?: return null
        val remoteId = id?.trim().orEmpty()
        if (remoteId.isEmpty()) return null
        val canonical = attributes.canonicalTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: attributes.titles?.en?.trim()?.takeIf { it.isNotEmpty() }
            ?: attributes.titles?.enJp?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val alt = buildMap {
            attributes.titles?.en?.trim()?.takeIf { it.isNotEmpty() && !it.equals(canonical, true) }
                ?.let { put("en", listOf(it)) }
            attributes.titles?.enJp?.trim()?.takeIf { it.isNotEmpty() && !it.equals(canonical, true) }
                ?.let { put("en_jp", listOf(it)) }
            attributes.titles?.jaJp?.trim()?.takeIf { it.isNotEmpty() && !it.equals(canonical, true) }
                ?.let { put("ja_jp", listOf(it)) }
        }
        return RemoteMetadata(
            sourceId = sourceId,
            remoteId = remoteId,
            title = canonical,
            altTitles = alt,
            englishTitle = attributes.titles?.en?.trim()?.takeIf { it.isNotEmpty() },
            description = cleanText(attributes.synopsis),
            coverUrl = attributes.posterImage?.original?.ifBlank { null }
                ?: attributes.posterImage?.large?.ifBlank { null },
            bannerUrl = attributes.coverImage?.original?.ifBlank { null }
                ?: attributes.coverImage?.large?.ifBlank { null },
            score = attributes.averageRating?.toDoubleOrNull()?.let { (it / 10.0).coerceIn(0.0, 10.0) },
            popularity = attributes.userCount?.takeIf { it >= 0 }?.toLong(),
            status = parseStatus(attributes.status),
            genres = categories,
            tags = categories,
            primaryGenre = categories.firstOrNull(),
            contentRating = when (attributes.ageRating?.uppercase()) {
                "R18" -> ExternalRating.PORNOGRAPHIC
                "R" -> ExternalRating.SUGGESTIVE
                "G", "PG" -> ExternalRating.SAFE
                else -> null
            },
            publicationType = parseSubtype(attributes.subtype),
            year = attributes.startDate?.substringBefore("-")?.toIntOrNull()?.takeIf { it in 1800..2200 },
            sourceUrl = "https://kitsu.io/manga/$remoteId",
        )
    }

    private fun parseStatus(raw: String?): MangaStatus = when (raw?.lowercase()) {
        "current", "ongoing" -> MangaStatus.ONGOING
        "finished", "complete", "completed" -> MangaStatus.COMPLETED
        "hiatus", "tba" -> MangaStatus.HIATUS
        "cancelled", "canceled" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    private fun parseSubtype(raw: String?): String? = when (raw?.lowercase()) {
        "manga" -> "Manga"
        "manhua" -> "Manhua"
        "manhwa" -> "Manhwa"
        "novel" -> "Light Novel"
        "oneshot", "one-shot" -> "One-shot"
        "doujin" -> "Doujinshi"
        "oel" -> "OEL"
        null, "" -> null
        else -> raw
    }

    @Serializable
    private data class KitsuListResponse(
        val data: List<KitsuResource> = emptyList(),
        val included: List<KitsuResource>? = null,
    )

    @Serializable
    private data class KitsuSingleResponse(
        val data: KitsuResource? = null,
        val included: List<KitsuResource>? = null,
    )

    @Serializable
    private data class KitsuResource(
        val id: String? = null,
        val type: String? = null,
        val attributes: KitsuAttributes? = null,
        val relationships: KitsuRelationships? = null,
    )

    @Serializable private data class KitsuRelationships(val categories: KitsuRelationship? = null)
    @Serializable private data class KitsuRelationship(val data: List<KitsuIdentifier> = emptyList())
    @Serializable private data class KitsuIdentifier(val id: String)

    @Serializable
    private data class KitsuAttributes(
        val title: String? = null,
        val ageRating: String? = null,
        @SerialName("canonicalTitle") val canonicalTitle: String? = null,
        val titles: KitsuTitles? = null,
        val synopsis: String? = null,
        val status: String? = null,
        val subtype: String? = null,
        @SerialName("startDate") val startDate: String? = null,
        @SerialName("averageRating") val averageRating: String? = null,
        @SerialName("userCount") val userCount: Int? = null,
        @SerialName("posterImage") val posterImage: KitsuImage? = null,
        @SerialName("coverImage") val coverImage: KitsuImage? = null,
    )

    @Serializable
    private data class KitsuTitles(
        val en: String? = null,
        @SerialName("en_jp") val enJp: String? = null,
        @SerialName("ja_jp") val jaJp: String? = null,
    )

    @Serializable
    private data class KitsuImage(
        val original: String? = null,
        val large: String? = null,
        val medium: String? = null,
    )
}
