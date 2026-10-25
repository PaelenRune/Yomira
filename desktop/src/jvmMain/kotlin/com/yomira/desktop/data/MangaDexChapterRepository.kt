package com.yomira.desktop.data

import com.yomira.core.model.Chapter
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.NetworkConfiguration
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** A reading failure with copy that is safe to render (never a credential-bearing URL). */
class DesktopReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * MangaDex chapter feed and at-home page resolver for the desktop reader.
 *
 * MangaDex is the only shared metadata source that also publishes chapter IDs
 * and page manifests through its public API, so it is the desktop app's native
 * reading path. Other providers keep their metadata-only contract: the detail
 * screen explains the limitation and opens the source page instead of inventing
 * a chapter list.
 */
class MangaDexChapterRepository(
    private val httpClient: OkHttpClient,
    private val config: NetworkConfiguration = NetworkConfiguration(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ChapterRepository {

    override suspend fun chapters(item: CatalogItem): List<DesktopChapter> = withContext(Dispatchers.IO) {
        if (item.sourceId != MetadataSources.MANGADEX) return@withContext emptyList()
        val url = "${config.mangaDexBaseUrl.trimEnd('/')}/manga/${item.remoteId}/feed"
            .toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("limit", CHAPTER_LIMIT.toString())
            ?.addQueryParameter("translatedLanguage[]", PREFERRED_LANGUAGE)
            ?.addQueryParameter("order[chapter]", "asc")
            ?.addQueryParameter("includeExternalUrl", "1")
            ?.addQueryParameter("includeFutureUpdates", "0")
            ?.addQueryParameter("includes[]", "scanlation_group")
            ?.build()
            ?: throw DesktopReadException("MangaDex adresi oluşturulamadı.")
        val payload = execute(url.toString(), "Bölüm listesi")
        val feed = decode<ChapterFeedResponse>(payload)
        feed.data.mapNotNull { dto ->
            val attributes = dto.attributes ?: return@mapNotNull null
            val number = attributes.chapter?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            if (!number.isFinite() || number < 0.0) return@mapNotNull null
            DesktopChapter(
                id = dto.id,
                mangaId = item.remoteId,
                number = number,
                title = attributes.title?.trim()?.takeIf { it.isNotEmpty() },
                group = dto.relationships
                    .firstOrNull { it.type == "scanlation_group" }
                    ?.attributes?.name?.trim()?.takeIf { it.isNotEmpty() },
                language = attributes.translatedLanguage,
                pageCount = attributes.pages ?: 0,
                externalUrl = attributes.externalUrl?.trim()?.takeIf { it.startsWith("https://") },
            )
        }
            .filter { it.language == null || it.language == PREFERRED_LANGUAGE }
            .sortedWith(compareBy(DesktopChapter::number).thenBy { it.group.orEmpty() })
            .distinctBy { Triple(it.number, it.title, it.group) }
    }

    override suspend fun pages(chapter: DesktopChapter): List<DesktopPage> = withContext(Dispatchers.IO) {
        if (chapter.externalUrl != null) {
            throw DesktopReadException("Bu bölüm scanlation grubunun sitesinde yayınlanıyor; tarayıcıda aç.")
        }
        val url = "${config.mangaDexBaseUrl.trimEnd('/')}/at-home/server/${chapter.id}"
            .toHttpUrlOrNull()?.toString()
            ?: throw DesktopReadException("Sayfa adresi oluşturulamadı.")
        val manifest = decode<AtHomeResponse>(execute(url, "Sayfalar"))
        val base = manifest.baseUrl.trimEnd('/')
        if (!base.startsWith("https://")) throw DesktopReadException("Kaynak güvenli olmayan bir adres döndürdü.")
        val hash = manifest.chapter?.hash?.takeIf { it.isNotBlank() }
            ?: throw DesktopReadException("Kaynak sayfa listesi döndürmedi.")
        // Prefer original pages; data-saver is a lower-resolution fallback for slow links.
        val original = manifest.chapter.data
        val files = original.takeIf { it.isNotEmpty() } ?: manifest.chapter.dataSaver
        if (files.isEmpty()) throw DesktopReadException("Bu bölümde okunabilir sayfa yok.")
        val directory = if (original.isNotEmpty()) "data" else "data-saver"
        files.mapIndexed { index, file ->
            DesktopPage(
                index = index,
                url = "$base/$directory/$hash/$file",
                headers = mapOf("Referer" to "https://mangadex.org/"),
            )
        }
    }

    private fun execute(url: String, operation: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", config.userAgent)
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DesktopReadException("$operation alınamadı (HTTP ${response.code}).")
                }
                response.body?.string().orEmpty()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: DesktopReadException) {
            throw error
        } catch (error: Exception) {
            throw DesktopReadException("$operation alınamadı. Bağlantını kontrol et.", error)
        }
    }

    private inline fun <reified T> decode(payload: String): T = try {
        json.decodeFromString<T>(payload)
    } catch (error: Exception) {
        throw DesktopReadException("Kaynak beklenmeyen bir yanıt döndürdü.", error)
    }

    @Serializable
    private data class ChapterFeedResponse(
        val result: String? = null,
        val data: List<ChapterDto> = emptyList(),
        val total: Int? = null,
    )

    @Serializable
    private data class ChapterDto(
        val id: String,
        val attributes: ChapterAttributes? = null,
        val relationships: List<RelationshipDto> = emptyList(),
    )

    @Serializable
    private data class ChapterAttributes(
        val chapter: String? = null,
        val title: String? = null,
        @SerialName("translatedLanguage") val translatedLanguage: String? = null,
        val pages: Int? = null,
        @SerialName("externalUrl") val externalUrl: String? = null,
    )

    @Serializable
    private data class RelationshipDto(
        val type: String? = null,
        val attributes: RelationshipAttributes? = null,
    )

    @Serializable
    private data class RelationshipAttributes(val name: String? = null)

    @Serializable
    private data class AtHomeResponse(
        val result: String? = null,
        @SerialName("baseUrl") val baseUrl: String,
        val chapter: AtHomeChapter,
    )

    @Serializable
    private data class AtHomeChapter(
        val hash: String? = null,
        val data: List<String> = emptyList(),
        @SerialName("dataSaver") val dataSaver: List<String> = emptyList(),
    )

    companion object {
        private const val CHAPTER_LIMIT = 500
        private const val PREFERRED_LANGUAGE = "en"

        /** Exposed for the detail screen copy; chapters are listed in this language. */
        const val LANGUAGE_LABEL = "İngilizce"
    }
}

/**
 * Adapts [MangaDexChapterRepository] chapters to the shared [Chapter] contract
 * used by sync and reading-progress logic. Kept tiny and side-effect free.
 */
fun DesktopChapter.toChapter(): Chapter = Chapter(
    id = id,
    mangaId = mangaId,
    chapterNumber = number,
    title = title,
    url = externalUrl ?: "https://mangadex.org/chapter/$id",
)
