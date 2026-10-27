package com.yomira.feature.reader

import com.yomira.core.database.entity.ChapterEntity
import com.yomira.domain.extension.*
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
) {
    suspend fun pages(sourceId: String, chapter: ChapterEntity): List<SourcePage> = withContext(Dispatchers.IO) {
        if (bridge.isPresent && extensions.isPresent) {
            val installed = extensions.get().installed().filter { it.trusted }.map { it.packageName }.toSet()
            val extension = extensions.get().available().firstOrNull {
                it.packageName in installed && it.sources.any { source -> source.id == sourceId }
            }
            if (extension != null) {
                val pages = bridge.get().getPageList(extension.packageName, sourceId,
                    SourceChapter(chapter.id, chapter.title ?: "Bölüm ${chapter.chapterNumber}", chapter.chapterNumber, chapter.releaseDate?.time))
                return@withContext validate(pages.sortedBy { it.index })
            }
        }
        requireHttps(chapter.url)
        client.newCall(Request.Builder().url(chapter.url).build()).readCancellable { response ->
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

    private fun validate(pages: List<SourcePage>): List<SourcePage> {
        check(pages.isNotEmpty()) { "Bölümde okunabilir sayfa bulunamadı." }
        pages.forEach { requireHttps(it.imageUrl) }
        return pages.mapIndexed { index, page -> page.copy(index = index) }
    }
}
