package com.yomira.feature.reader

import com.yomira.core.database.entity.ChapterEntity
import com.yomira.core.database.dao.MangaDao
import com.yomira.domain.extension.*
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderSourceModule {
    @BindsOptionalOf abstract fun sourceBridge(): SourceBridge
    @BindsOptionalOf abstract fun extensionRepository(): ExtensionRepository
}

class ReaderPageRepository @Inject constructor(
    private val client: OkHttpClient,
    private val bridge: Optional<SourceBridge>,
    private val extensions: Optional<ExtensionRepository>,
    private val mangaDao: MangaDao? = null,
) {
    suspend fun pages(sourceId: String, chapter: ChapterEntity): List<SourcePage> = withContext(Dispatchers.IO) {
        try {
            primaryPages(sourceId, chapter)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val recovered = attempt { alternativePages(sourceId, chapter) }
            recovered ?: throw error
        }
    }

    private suspend fun primaryPages(sourceId: String, chapter: ChapterEntity): List<SourcePage> {
        val location = SourceChapterLocation.decode(chapter.url)
        if (location != null) {
            check(bridge.isPresent && extensions.isPresent) { "Source extension is unavailable" }
            check(extensions.get().installed().any { it.packageName == location.packageName && it.trusted }) {
                "Source extension is not installed or trusted"
            }
            val sourceChapter = SourceChapter(location.chapterId, chapter.title.orEmpty(), chapter.chapterNumber, chapter.releaseDate?.time)
            return validate(bridge.get().getPageList(location.packageName, location.sourceId, sourceChapter).sortedBy { it.index })
        }
        if (bridge.isPresent && extensions.isPresent) {
            val installed = extensions.get().installed().filter { it.trusted }.map { it.packageName }.toSet()
            val extension = extensions.get().available().firstOrNull {
                it.packageName in installed && it.sources.any { source -> source.id == sourceId }
            }
            if (extension != null) {
                val pages = bridge.get().getPageList(extension.packageName, sourceId,
                    SourceChapter(chapter.id, chapter.title ?: "Bölüm ${chapter.chapterNumber}", chapter.chapterNumber, chapter.releaseDate?.time))
                return validate(pages.sortedBy { it.index })
            }
        }
        requireHttps(chapter.url)
        return client.newCall(Request.Builder().url(chapter.url).build()).readCancellable { response ->
            check(response.isSuccessful) { "Bölüm yüklenemedi (HTTP ${response.code})." }
            val body = checkNotNull(response.body) { "Kaynak boş yanıt döndürdü." }
            val finalUrl = response.request.url.toString()
            requireHttps(finalUrl)
            if (body.contentType()?.type == "image") return@readCancellable listOf(SourcePage(0, finalUrl))
            val document = Jsoup.parse(body.string(), finalUrl)
            // Restrict extraction to actual reading containers; never treat cover/ad images as pages.
            val images = document.select(".reading-content img, #readerarea img, .reader-area img, .chapter-content img, .page-break img, #comic img, img.manga-page, img.chapter-img")
            val urls = images.mapNotNull { image ->
                listOf("data-src", "data-original", "data-lazy-src", "src").firstNotNullOfOrNull { attribute ->
                    image.absUrl(attribute).trim().takeIf { it.startsWith("https://") }
                }
            }.distinct()
            check(urls.isNotEmpty()) { "Bu kaynak sayfa listesini paylaşmıyor. Kaynağın güvenilir okuyucu eklentisini kurup yeniden dene." }
            validate(urls.mapIndexed { index, url -> SourcePage(index, url, mapOf("Referer" to finalUrl)) })
        }
    }

    private data class Alternative(
        val packageName: String,
        val sourceId: String,
        val chapter: SourceChapter,
        val chapterCount: Int,
        val latestUpdate: Long?,
    )

    private suspend fun alternativePages(sourceId: String, chapter: ChapterEntity): List<SourcePage>? {
        if (!bridge.isPresent || !extensions.isPresent) return null
        val manga = mangaDao?.getById(chapter.mangaId) ?: return null
        val titles = (listOf(manga.title) + manga.altTitles).filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) return null
        val failed = SourceChapterLocation.decode(chapter.url)
        val installed = extensions.get().installed().filter { it.trusted }
        val legacy = if (installed.any { it.descriptor == null }) extensions.get().available() else emptyList()
        val descriptors = installed.mapNotNull { entry ->
            entry.descriptor?.takeIf { it.packageName == entry.packageName }
                ?: legacy.firstOrNull { it.packageName == entry.packageName }
        }
        // Never silently change the reader's chosen language when its descriptor is known.
        val language = descriptors.flatMap { it.sources }.firstOrNull { it.id == (failed?.sourceId ?: sourceId) }?.language
        val alternatives = mutableListOf<Alternative>()
        val queries = QueryFanout.generate(titles)
        for (extension in descriptors) {
            for (source in extension.sources) {
                if (failed != null && extension.packageName == failed.packageName && source.id == failed.sourceId) continue
                if (failed == null && source.id == sourceId) continue
                if (language != null && source.language != language) continue
                attempt {
                    withTimeout(15_000) {
                        val visited = mutableSetOf<String>()
                        for (query in queries) {
                            for (candidate in bridge.get().getMangaList(extension.packageName, source.id, query)) {
                                if (!SmartSourceMatcher.matchesTitle(candidate.title, titles) || !visited.add(candidate.id)) continue
                                val chapters = bridge.get().getChapterList(extension.packageName, source.id, candidate).distinctBy { it.id }
                                val matching = chapters.firstOrNull { it.number == chapter.chapterNumber } ?: continue
                                alternatives += Alternative(extension.packageName, source.id, matching, chapters.size,
                                    chapters.mapNotNull { it.uploadedAt }.maxOrNull())
                            }
                        }
                    }
                }
            }
        }
        for (alternative in alternatives.sortedWith(compareByDescending<Alternative> { it.chapterCount }
            .thenByDescending { it.latestUpdate ?: Long.MIN_VALUE })) {
            val pages = attempt {
                withTimeout(15_000) {
                    validate(bridge.get().getPageList(alternative.packageName, alternative.sourceId, alternative.chapter).sortedBy { it.index })
                }
            }
            if (pages != null) return pages
        }
        return null
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    private fun validate(pages: List<SourcePage>): List<SourcePage> {
        check(pages.isNotEmpty()) { "Bölümde okunabilir sayfa bulunamadı." }
        pages.forEach { requireHttps(it.imageUrl) }
        return pages.mapIndexed { index, page -> page.copy(index = index) }
    }
}
