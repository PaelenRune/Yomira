package com.yomira.app.catalog

import com.yomira.domain.extension.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepositorySourceDiscoveryTest {
    @Test fun `discovery probes arbitrary sites and preserves fuzzy matches after another site fails`() = runTest {
        val source = ExtensionSource("new-source", "Any New Site", "tr")
        val blocked = ExtensionSource("blocked", "Blocked Site", "tr")
        val extension = ExtensionDescriptor("example.extension", "Example", "tr", false, "1", 1,
            "https://example.org/extension.apk", listOf(blocked, source))
        val queried = mutableSetOf<String>()
        val bridge = object : SourceBridge {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
                queried += sourceId
                if (sourceId == "blocked") error("Blocked")
                return listOf(SourceManga("manga", "Haikyuu!! (Digital)", "https://example.org/manga"))
            }
            override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) =
                listOf(SourceChapter("1", "Chapter 1", 1.0, 123L))
            override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter) = emptyList<SourcePage>()
        }
        val result = RepositorySourceDiscovery(bridge).discover(SeriesIdentity("series", "Haikyu!!"), listOf(extension))
        assertEquals(setOf("blocked", "new-source"), queried)
        assertEquals(source, result.single().source)
        assertEquals(SourceHealth.HEALTHY, result.single().health)
        assertNotNull(result.single().responseTimeMillis)
    }

    @Test(expected = CancellationException::class)
    fun `discovery does not hide caller cancellation`() = runTest {
        val source = ExtensionSource("source", "Any Site", "tr")
        val extension = ExtensionDescriptor("example.extension", "Example", "tr", false, "1", 1,
            "https://example.org/extension.apk", listOf(source))
        val bridge = object : SourceBridge {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> =
                throw CancellationException("cancelled")
            override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) = emptyList<SourceChapter>()
            override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter) = emptyList<SourcePage>()
        }
        RepositorySourceDiscovery(bridge).discover(SeriesIdentity("series", "Title"), listOf(extension))
    }
}
