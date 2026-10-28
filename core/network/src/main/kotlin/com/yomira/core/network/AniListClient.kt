package com.yomira.core.network

import com.yomira.core.model.MangaStatus
import com.yomira.core.model.AdaptationKind
import com.yomira.core.model.WatchableAdaptation
import com.yomira.core.model.RecommendedSeries
import com.yomira.core.model.RelatedSeries
import com.yomira.core.model.RelationRole
import com.yomira.core.model.SeriesConnections
import com.yomira.core.model.PageWindow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** One discovery page plus AniList's own continuation flag. */
data class AniListPage(
    val records: List<RemoteMetadata>,
    val page: Int,
    val hasNextPage: Boolean,
)

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
        discover(page = 1, perPage = limit, includeAdult = false).records

    /**
     * One GraphQL page of the discovery feed. [PageWindow] keeps the 1-based
     * page math identical to the REST providers' offset math.
     */
    suspend fun discover(
        page: Int = 1,
        perPage: Int = PageWindow.DEFAULT_PAGE_SIZE,
        includeAdult: Boolean = false,
    ): AniListPage = withContext(Dispatchers.IO) {
        val window = PageWindow(page, perPage.coerceIn(1, PageWindow.MAX_PAGE_SIZE))
        val variables = buildJsonObject {
            put("page", JsonPrimitive(window.page))
            put("perPage", JsonPrimitive(window.pageSize))
            put("isAdult", JsonPrimitive(includeAdult))
        }
        val payload = execute(DISCOVER_QUERY, variables)?.page
        AniListPage(
            records = payload?.media.orEmpty().mapNotNull { it.toMetadata() },
            page = window.page,
            hasNextPage = payload?.pageInfo?.hasNextPage ?: false,
        )
    }

    override suspend fun findById(remoteId: String): RemoteMetadata? = withContext(Dispatchers.IO) {
        val id = remoteId.toIntOrNull() ?: return@withContext null
        val variables = buildJsonObject { put("id", JsonPrimitive(id)) }
        execute(BY_ID_QUERY, variables)?.media?.toMetadata()
    }

    /**
     * Franchise relations and community recommendations of one AniList manga.
     *
     * Feeds the detail page's "Related series" strip (prequels, sequels, spin-offs
     * with their relation badge) and the "Series like this" strip. Watchable
     * relations are kept separate so they never open the manga reader.
     */
    suspend fun connections(anilistId: Int, limit: Int = 12): SeriesConnections =
        withContext(Dispatchers.IO) {
            val variables = buildJsonObject {
                put("id", JsonPrimitive(anilistId))
                put("perPage", JsonPrimitive(limit.coerceIn(1, 25)))
            }
            val media = execute(CONNECTIONS_QUERY, variables)?.media ?: return@withContext SeriesConnections()
            val related = media.relations?.edges.orEmpty().mapNotNull { edge ->
                val node = edge.node?.takeIf { it.type.equals("MANGA", ignoreCase = true) } ?: return@mapNotNull null
                val id = node.id ?: return@mapNotNull null
                val title = node.title?.display() ?: return@mapNotNull null
                RelatedSeries(
                    id = "$sourceId:$id",
                    title = title,
                    role = parseRelationRole(edge.relationType),
                    coverUrl = node.coverImage?.extraLarge?.ifBlank { null } ?: node.coverImage?.large?.ifBlank { null },
                    bannerUrl = node.bannerImage?.ifBlank { null },
                )
            }.distinctBy { it.id }
            val recommended = media.recommendations?.nodes.orEmpty().mapNotNull { node ->
                val entry = node.mediaRecommendation ?: return@mapNotNull null
                val id = entry.id ?: return@mapNotNull null
                val title = entry.title?.display() ?: return@mapNotNull null
                RecommendedSeries(
                    id = "$sourceId:$id",
                    title = title,
                    coverUrl = entry.coverImage?.extraLarge?.ifBlank { null } ?: entry.coverImage?.large?.ifBlank { null },
                    bannerUrl = entry.bannerImage?.ifBlank { null },
                    score = entry.averageScore?.let { (it / 10.0).coerceIn(0.0, 10.0) },
                )
            }.distinctBy { it.id }
            val adaptations = media.relations?.edges.orEmpty().mapNotNull { edge ->
                val node = edge.node ?: return@mapNotNull null
                // ADAPTATION is bidirectional: a manga adaptation is still readable,
                // not a watchable entry. Missing types can occur in older payloads.
                if (!node.type.equals("ANIME", ignoreCase = true) &&
                    !(edge.relationType.equals("ADAPTATION", ignoreCase = true) &&
                        !node.type.equals("MANGA", ignoreCase = true))
                ) return@mapNotNull null
                val id = node.id ?: return@mapNotNull null
                val title = node.title?.display() ?: return@mapNotNull null
                WatchableAdaptation(
                    id = "$sourceId:$id",
                    title = title,
                    kind = node.adaptationKind(),
                    format = node.format?.trim()?.uppercase()?.ifEmpty { null },
                    episodes = node.episodes?.takeIf { it > 0 },
                    releaseYear = node.startDate?.year?.takeIf { it > 0 },
                    score = node.averageScore?.let { (it / 10.0).coerceIn(0.0, 10.0) },
                    coverUrl = node.coverImage?.extraLarge?.ifBlank { null } ?: node.coverImage?.large?.ifBlank { null },
                    bannerUrl = node.bannerImage?.ifBlank { null },
                )
            }.distinctBy { it.id }
            SeriesConnections(related = related, recommended = recommended, adaptations = adaptations)
        }

    private fun AniListMedia.adaptationKind(): AdaptationKind {
        val format = format?.trim()?.uppercase()
        val labels = (genres + tags.mapNotNull { it.name }).map { it.trim() }
        return when {
            labels.any { it.equals("Hentai", ignoreCase = true) } ||
                (format == "OVA" && (isAdult || tags.any { it.isAdult } ||
                    labels.any { it.equals("Explicit", ignoreCase = true) })) -> AdaptationKind.HENTAI_OVA
            format in setOf("DRAMA", "LIVE_ACTION", "LIVE ACTION", "K_DRAMA", "K-DRAMA") ||
                labels.any { it.equals("Live Action", ignoreCase = true) ||
                    it.equals("Live-Action", ignoreCase = true) ||
                    it.equals("K-Drama", ignoreCase = true) } -> AdaptationKind.LIVE_ACTION
            countryOfOrigin.equals("CN", ignoreCase = true) || format == "DONGHUA" -> AdaptationKind.DONGHUA
            format == "SPECIAL" || format == "TV_SPECIAL" -> AdaptationKind.SPECIAL
            // Drama alone is also a common anime genre, so only use it when the
            // provider has not explicitly identified an animation format.
            format !in setOf("TV", "MOVIE", "OVA", "ONA") &&
                labels.any { it.equals("Drama", ignoreCase = true) } -> AdaptationKind.LIVE_ACTION
            // ONA means web animation, not necessarily Chinese animation.
            else -> AdaptationKind.ANIME
        }
    }

    /** English display title first, matching the shelf title rule of the waterfall. */
    private fun AniListTitle.display(): String? = sequenceOf(english, romaji, native)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()

    private fun parseRelationRole(raw: String?): RelationRole = when (raw?.uppercase()) {
        "PREQUEL" -> RelationRole.PREQUEL
        "SEQUEL" -> RelationRole.SEQUEL
        "SPIN_OFF" -> RelationRole.SPIN_OFF
        "SIDE_STORY" -> RelationRole.SIDE_STORY
        "ADAPTATION" -> RelationRole.ADAPTATION
        "ALTERNATIVE" -> RelationRole.ALTERNATIVE
        "SOURCE" -> RelationRole.SOURCE
        "PARENT" -> RelationRole.PARENT
        "CHARACTER" -> RelationRole.CHARACTER
        else -> RelationRole.OTHER
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
        val english = title?.english?.trim()?.takeIf { it.isNotEmpty() }
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
            // AniList keeps the romaji in `title`, but the shelf must show the English name.
            englishTitle = english,
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
            publicationType = publicationType(format, countryOfOrigin),
            year = startDate?.year,
            sourceUrl = "https://anilist.co/manga/$id",
        )
    }

    /**
     * AniList reports the country of origin separately from the format. A US/UK
     * `MANGA` record is a western comic for shelf purposes, while a Japanese
     * record keeps its original format.
     */
    private fun publicationType(format: String?, countryOfOrigin: String?): String? {
        if (countryOfOrigin?.trim()?.uppercase() in WESTERN_COUNTRIES) return "Comic"
        return format?.trim()?.ifEmpty { null }
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
    private data class AniListData(val media: AniListMedia? = null, val page: AniListPagePayload? = null)

    @Serializable
    private data class AniListPagePayload(
        val media: List<AniListMedia> = emptyList(),
        val pageInfo: AniListPageInfo? = null,
    )

    @Serializable
    private data class AniListPageInfo(val hasNextPage: Boolean = false, val total: Int? = null)

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
        val tags: List<AniListTag> = emptyList(),
        val isAdult: Boolean = false,
        val episodes: Int? = null,
        val format: String? = null,
        val countryOfOrigin: String? = null,
        val startDate: AniListDate? = null,
        val staff: AniListStaff? = null,
        val type: String? = null,
        val relations: AniListRelations? = null,
        val recommendations: AniListRecommendations? = null,
    )

    @Serializable
    private data class AniListTag(val name: String? = null, val isAdult: Boolean = false)

    @Serializable
    private data class AniListRelations(val edges: List<AniListRelationEdge> = emptyList())

    @Serializable
    private data class AniListRelationEdge(val relationType: String? = null, val node: AniListMedia? = null)

    @Serializable
    private data class AniListRecommendations(val nodes: List<AniListRecommendationNode> = emptyList())

    @Serializable
    private data class AniListRecommendationNode(val mediaRecommendation: AniListMedia? = null)

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
                  countryOfOrigin
                  startDate { year }
                  staff(perPage: 6) {
                    edges { role node { name { full } } }
                  }
                }
              }
            }
        """.trimIndent()

        val DISCOVER_QUERY = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}isAdult: Boolean) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { currentPage hasNextPage total }
                media(type: MANGA, sort: POPULARITY_DESC, isAdult: ${'$'}isAdult) {
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
                  countryOfOrigin
                  startDate { year }
                  staff(perPage: 6) {
                    edges { role node { name { full } } }
                  }
                }
              }
            }
        """.trimIndent()

        val CONNECTIONS_QUERY = """
            query (${'$'}id: Int, ${'$'}perPage: Int) {
              Media(id: ${'$'}id, type: MANGA) {
                id
                relations(perPage: ${'$'}perPage) {
                  edges {
                    relationType
                    node {
                      id
                      type
                      format
                      status
                      episodes
                      genres
                      tags { name isAdult }
                      isAdult
                      countryOfOrigin
                      startDate { year }
                      averageScore
                      title { romaji english native }
                      coverImage { extraLarge large }
                      bannerImage
                    }
                  }
                }
                recommendations(sort: RATING_DESC, perPage: ${'$'}perPage) {
                  nodes {
                    mediaRecommendation {
                      id
                      title { romaji english native }
                      coverImage { extraLarge large }
                      bannerImage
                      averageScore
                    }
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
                countryOfOrigin
                startDate { year }
                staff(perPage: 6) {
                  edges { role node { name { full } } }
                }
              }
            }
        """.trimIndent()

        /** Countries whose comics are shelved as western (left-to-right) comics. */
        val WESTERN_COUNTRIES = setOf(
            "US", "GB", "CA", "AU", "IE", "NZ",
            "FR", "BE", "ES", "IT", "DE", "BR", "MX", "AR",
        )
    }
}
