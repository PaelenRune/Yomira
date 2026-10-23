package com.yomira.core.network

import com.yomira.core.model.MangaStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * AniList GraphQL client.
 *
 * AniList is the preferred source for artwork in the waterfall because its
 * `extraLarge` cover and `bannerImage` fields are typically the highest
 * resolution catalogue images available without downloading media.
 */
class AniListClient(
    private val httpClient: OkHttpClient,
    private val json: Json = NetworkJson.instance,
    private val config: NetworkConfiguration = NetworkConfiguration(),
) : MetadataProvider {

    override val sourceId: String = MetadataSources.ANILIST

    override suspend fun search(query: String, limit: Int): List<RemoteMetadata> =
        withContext(Dispatchers.IO) {
            val variables = buildJsonObject {
                put("search", JsonPrimitive(query))
                put("perPage", JsonPrimitive(limit.coerceIn(1, 20)))
            }
            execute(SEARCH_QUERY, variables)?.page?.media.orEmpty().mapNotNull { it.toMetadata() }
        }

    suspend fun trending(limit: Int = 20): List<RemoteMetadata> =
        withContext(Dispatchers.IO) {
            val variables = buildJsonObject { put("perPage", JsonPrimitive(limit.coerceIn(1, 50))) }
            execute(TRENDING_QUERY, variables)?.page?.media.orEmpty().mapNotNull { it.toMetadata() }
        }

    override suspend fun findById(remoteId: String): RemoteMetadata? = withContext(Dispatchers.IO) {
        val id = remoteId.toIntOrNull() ?: return@withContext null
        val variables = buildJsonObject { put("id", JsonPrimitive(id)) }
        execute(BY_ID_QUERY, variables)?.media?.toMetadata()
    }

    private suspend fun execute(query: String, variables: kotlinx.serialization.json.JsonObject): AniListData? {
        val payload = buildJsonObject {
            put("query", JsonPrimitive(query))
            put("variables", variables)
        }.toString()
        val request = Request.Builder()
            .url(config.aniListEndpoint)
            .commonHeaders(config.userAgent)
            .post(payload.asJsonBody())
            .build()
        return httpClient.executeChecked(request, sourceId).use { response ->
            response.decodeJson<AniListResponse>(json, sourceId)
        }.data
    }

    private fun AniListMedia.toMetadata(): RemoteMetadata? {
        val canonical = title?.romaji?.trim().orEmpty()
            .ifEmpty { title?.english?.trim().orEmpty() }
            .ifEmpty { title?.native?.trim().orEmpty() }
        val id = id ?: return null
        if (canonical.isEmpty()) return null
        val alt = buildMap {
            title?.english?.takeIf { it.isNotBlank() && !it.equals(canonical, true) }
                ?.let { put("en", listOf(it)) }
            title?.native?.takeIf { it.isNotBlank() && !it.equals(canonical, true) }
                ?.let { put("ja", listOf(it)) }
        }
        val staffEdges = staff?.edges.orEmpty()
        val authors = staffEdges.filter { edge ->
            edge.role?.contains("story", ignoreCase = true) == true ||
                edge.role?.contains("original", ignoreCase = true) == true
        }.mapNotNull { it.node?.name?.full?.trim() }.distinct()
        val artists = staffEdges.filter { edge ->
            edge.role?.contains("art", ignoreCase = true) == true
        }.mapNotNull { it.node?.name?.full?.trim() }.distinct()
        return RemoteMetadata(
            sourceId = sourceId,
            remoteId = id.toString(),
            title = canonical,
            altTitles = alt,
            author = authors.joinToString(", ").ifBlank { null },
            artist = artists.joinToString(", ").ifBlank { null },
            description = cleanText(description),
            coverUrl = coverImage?.extraLarge?.ifBlank { null } ?: coverImage?.large?.ifBlank { null },
            bannerUrl = bannerImage?.ifBlank { null },
            score = averageScore?.let { (it / 10.0 * 10).roundToInt() / 10.0 }?.coerceIn(0.0, 10.0),
            popularity = popularity?.toLong(),
            status = parseStatus(status),
            genres = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            tags = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            publicationType = format?.trim()?.ifEmpty { null },
            year = startDate?.year,
            sourceUrl = "https://anilist.co/manga/$id",
        )
    }

    private fun parseStatus(raw: String?): MangaStatus = when (raw?.uppercase()) {
        "RELEASING" -> MangaStatus.ONGOING
        "FINISHED" -> MangaStatus.COMPLETED
        "HIATUS" -> MangaStatus.HIATUS
        "CANCELLED" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    @Serializable
    private data class AniListResponse(val data: AniListData? = null)

    @Serializable
    private data class AniListData(val media: AniListMedia? = null, val page: AniListPage? = null)

    @Serializable
    private data class AniListPage(val media: List<AniListMedia> = emptyList())

    @Serializable
    private data class AniListMedia(
        val id: Int? = null,
        val title: AniListTitle? = null,
        val description: String? = null,
        val coverImage: AniListCover? = null,
        val bannerImage: String? = null,
        val averageScore: Int? = null,
        val popularity: Int? = null,
        val status: String? = null,
        val genres: List<String> = emptyList(),
        val format: String? = null,
        val startDate: AniListDate? = null,
        val staff: AniListStaff? = null,
    )

    @Serializable
    private data class AniListTitle(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
    )

    @Serializable
    private data class AniListCover(
        val extraLarge: String? = null,
        val large: String? = null,
        val color: String? = null,
    )

    @Serializable
    private data class AniListDate(val year: Int? = null)

    @Serializable
    private data class AniListStaff(val edges: List<AniListStaffEdge> = emptyList())

    @Serializable
    private data class AniListStaffEdge(val role: String? = null, val node: AniListStaffNode? = null)

    @Serializable
    private data class AniListStaffNode(val name: AniListStaffName? = null)

    @Serializable
    private data class AniListStaffName(val full: String? = null)

    private companion object {
        val SEARCH_QUERY = """
            query (${'$'}search: String, ${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(search: ${'$'}search, type: MANGA, sort: SEARCH_MATCH) {
                  id
                  title { romaji english native }
                  description(asHtml: false)
                  coverImage { extraLarge large color }
                  bannerImage
                  averageScore
                  popularity
                  status
                  genres
                  format
                  startDate { year }
                  staff(perPage: 6) {
                    edges { role node { name { full } } }
                  }
                }
              }
            }
        """.trimIndent()

        val TRENDING_QUERY = """
            query (${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(type: MANGA, sort: TRENDING_DESC, isAdult: false) {
                  id
                  title { romaji english native }
                  description(asHtml: false)
                  coverImage { extraLarge large color }
                  bannerImage
                  averageScore
                  popularity
                  status
                  genres
                  format
                  startDate { year }
                  staff(perPage: 6) {
                    edges { role node { name { full } } }
                  }
                }
              }
            }
        """.trimIndent()

        val BY_ID_QUERY = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: MANGA) {
                id
                title { romaji english native }
                description(asHtml: false)
                coverImage { extraLarge large color }
                bannerImage
                averageScore
                popularity
                status
                genres
                format
                startDate { year }
                staff(perPage: 6) {
                  edges { role node { name { full } } }
                }
              }
            }
        """.trimIndent()
    }
}
