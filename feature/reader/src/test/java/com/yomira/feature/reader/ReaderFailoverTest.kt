package com.yomira.feature.reader

import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.entity.ChapterEntity
import com.yomira.core.database.entity.MangaEntity
import com.yomira.domain.extension.*
import java.lang.reflect.Proxy
import java.util.Optional
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class ReaderFailoverTest {
    private val chapter = ChapterEntity("chapter", "catalog:1", 4.5,
        url = SourceChapterLocation("primary", "primary", "original").encode())
    private val requests = mutableListOf<String>()
    private var emptyPrimary = false
    private var cancelPrimary = false
    private var candidateTitle = "Test Manga"
    private var candidateNumber = 4.5
    private var failAlternatives = false
    private val originalError = IllegalStateException("HTTP 404")

    private fun descriptor(id: String) = ExtensionDescriptor(id, id, "en", false, "1", 1,
        "https://example.org/$id.apk", listOf(ExtensionSource(id, id, "en")))

    private val extensions = object : ExtensionRepository {
        override suspend fun addRepository(indexUrl: String) = Unit
        override suspend fun removeRepository(indexUrl: String) = Unit
        override suspend fun repositories() = emptyList<String>()
        override suspend fun refresh() = emptyList<ExtensionDescriptor>()
        override suspend fun available(): List<ExtensionDescriptor> = error("Installed descriptors must work offline")
        override suspend fun installed() = listOf("primary", "broken", "backup", "untrusted").map {
            InstalledExtension(it, 1, it != "untrusted", descriptor(it))
        }
    }
    private val bridge = object : SourceBridge {
        override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
            check(packageName != "primary" && packageName != "untrusted")
            return listOf(SourceManga("manga", candidateTitle, "https://example.org/manga"))
        }
        override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) =
            listOf(SourceChapter("alternative", "Chapter", candidateNumber, 10L)) +
                if (packageName == "broken") listOf(SourceChapter("extra", "Chapter", 1.0, 1L)) else emptyList()
        override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter): List<SourcePage> {
            requests += packageName
            if (packageName == "primary") {
                if (cancelPrimary) throw CancellationException("Reader closed")
                if (emptyPrimary) return emptyList()
                throw originalError
            }
            check(chapter.id == "alternative")
            if (packageName == "broken" || failAlternatives) error("Backup unavailable")
            return listOf(SourcePage(9, "https://example.org/2.jpg", mapOf("Referer" to "https://example.org")),
                SourcePage(2, "https://example.org/1.jpg"))
        }
    }
    private fun repository(): ReaderPageRepository {
        val dao = Proxy.newProxyInstance(MangaDao::class.java.classLoader, arrayOf(MangaDao::class.java)) { _, method, _ ->
            check(method.name == "getById")
            MangaEntity("catalog:1", "Test Manga", sourceId = "catalog")
        } as MangaDao
        return ReaderPageRepository(OkHttpClient(), Optional.of(bridge), Optional.of(extensions), dao)
    }

    @Test fun http404SilentlyTriesRankedTrustedAlternativesAndPreservesHeaders() = runTest {
        val pages = repository().pages("catalog", chapter)
        assertEquals(listOf("primary", "broken", "backup"), requests)
        assertEquals(listOf(0, 1), pages.map { it.index })
        assertEquals("https://example.org/1.jpg", pages.first().imageUrl)
        assertEquals("https://example.org", pages.last().headers["Referer"])
    }

    @Test fun emptyPageListAlsoRecovers() = runTest {
        emptyPrimary = true
        assertEquals(2, repository().pages("catalog", chapter).size)
    }

    @Test fun wrongMangaCannotBeUsedForRecovery() = runTest {
        candidateTitle = "Unrelated Series"
        assertOriginalError()
        assertEquals(listOf("primary"), requests)
    }

    @Test fun chapterNumberMustMatchExactlyIncludingFraction() = runTest {
        candidateNumber = 4.0
        assertOriginalError()
        assertEquals(listOf("primary"), requests)
    }

    @Test fun exhaustedAlternativesKeepOriginalFailure() = runTest {
        failAlternatives = true
        assertOriginalError()
        assertEquals(listOf("primary", "broken", "backup"), requests)
    }

    @Test fun cancellationNeverStartsFailover() = runTest {
        cancelPrimary = true
        try {
            repository().pages("catalog", chapter)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf("primary"), requests)
        }
    }

    private suspend fun assertOriginalError() {
        try {
            repository().pages("catalog", chapter)
            fail("Expected original error")
        } catch (error: IllegalStateException) {
            // Coroutine stack-trace recovery can copy exceptions across withContext.
            assertEquals(originalError.message, error.message)
            assertEquals(originalError.javaClass, error.javaClass)
        }
    }
}
