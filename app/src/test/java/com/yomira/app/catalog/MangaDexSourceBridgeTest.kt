package com.yomira.app.catalog

import com.yomira.domain.extension.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class MangaDexSourceBridgeTest {
    // Real OkHttp callbacks must not race runTest's auto-advancing virtual timeout.
    @Test fun productionFallbackResolvesSelectedLanguageAndLoadsRealPages() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"$MANGA","attributes":{"title":{"ja":"Original"},"altTitles":[{"en":"Alias"}]}}]}"""))
            server.enqueue(MockResponse().setBody("""{"total":1,"data":[{"id":"$CHAPTER","attributes":{"chapter":"1","title":"First","pages":2,"translatedLanguage":"tr","publishAt":"2026-01-01T00:00:00Z"}}]}"""))
            server.enqueue(MockResponse().setBody("""{"baseUrl":"https://images.example.org","chapter":{"hash":"abcdef","data":["one.jpg","two.jpg"]}}"""))
            val repository = NativeSourceRepository(EmptyRepository())
            val bridge = MangaDexSourceBridge(OkHttpClient(), repository, store(), server.url("/").toString())
            val matcher = SmartSourceMatcher(repository, bridge, RepositorySourceDiscovery(bridge))
            val match = matcher.match(SeriesIdentity("catalog", "Alias"), ReaderPreferences(ReadingLanguage.TURKISH))
            assertTrue("Expected Ready, got $match", match is SmartMatchResult.Ready)
            match as SmartMatchResult.Ready
            assertEquals("tr", match.selected.source.language)
            assertEquals(1, match.chapters.size)
            val pages = bridge.getPageList(match.selected.extension.packageName, match.selected.source.id, match.chapters.single())
            assertEquals(listOf("https://images.example.org/data/abcdef/one.jpg", "https://images.example.org/data/abcdef/two.jpg"), pages.map { it.imageUrl })
            assertEquals(listOf("tr"), server.takeRequest().requestUrl!!.queryParameterValues("availableTranslatedLanguage[]"))
            assertEquals(listOf("tr"), server.takeRequest().requestUrl!!.queryParameterValues("translatedLanguage[]"))
            assertEquals("/at-home/server/$CHAPTER", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test fun feedIsPaginatedAndWrongLanguageOrExternalChaptersAreExcluded() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"total":3,"data":[{"id":"one","attributes":{"pages":2,"translatedLanguage":"en","chapter":"1"}},{"id":"other-language","attributes":{"pages":2,"translatedLanguage":"tr"}}]}"""))
            server.enqueue(MockResponse().setBody("""{"total":3,"data":[{"id":"external","attributes":{"pages":0,"translatedLanguage":"en"}}]}"""))
            val bridge = MangaDexSourceBridge(OkHttpClient(), NativeSourceRepository(EmptyRepository()), store(), server.url("/").toString())
            val chapters = bridge.getChapterList(NativeSourceRepository.PACKAGE, "mangadex-en", SourceManga(MANGA, "Title", "https://mangadex.org/title/$MANGA"))
            assertEquals(listOf("one"), chapters.map { it.id })
            assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("offset"))
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("offset"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun repositoryDiscoverySearchesAliasesAndUsesObservedChapterCounts() = runTest {
        val source = ExtensionSource("md", "MangaDex", "en")
        val extension = ExtensionDescriptor("upstream.mangadex", "MangaDex", "en", false, "1", 1, "https://example.org/a.apk", listOf(source))
        val queries = mutableListOf<String>()
        val bridge = object : SourceBridge {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
                queries += query
                return if (query == "Alias") listOf(SourceManga("id", "Alias", "https://example.org/title")) else emptyList()
            }
            override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) = listOf(SourceChapter("c", "One", 1.0, 42))
            override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter) = emptyList<SourcePage>()
        }
        val candidates = RepositorySourceDiscovery(bridge).discover(SeriesIdentity("id", "Primary", setOf("Alias")), listOf(extension))
        assertEquals(1, candidates.single().chapterCount)
        assertEquals(42L, candidates.single().latestChapterAt)
        assertTrue("Alias" in queries)
    }

    private fun store() = object : OnboardingStore {
        override suspend fun read() = OnboardingState(ReaderPreferences(ReadingLanguage.TURKISH), null, emptyList(), true)
        override suspend fun write(state: OnboardingState) = Unit
    }

    private class EmptyRepository : ExtensionRepository {
        override suspend fun addRepository(indexUrl: String) = Unit
        override suspend fun removeRepository(indexUrl: String) = Unit
        override suspend fun repositories() = emptyList<String>()
        override suspend fun refresh() = emptyList<ExtensionDescriptor>()
        override suspend fun available() = emptyList<ExtensionDescriptor>()
        override suspend fun installed() = emptyList<InstalledExtension>()
    }

    companion object {
        const val MANGA = "11111111-1111-1111-1111-111111111111"
        const val CHAPTER = "22222222-2222-2222-2222-222222222222"
    }
}
