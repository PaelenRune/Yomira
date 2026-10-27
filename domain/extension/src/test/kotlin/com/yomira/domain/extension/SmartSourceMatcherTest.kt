package com.yomira.domain.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SmartSourceMatcherTest {
    private val preferences = ReaderPreferences(ReadingLanguage.TURKISH)
    private val series = SeriesIdentity("series:1", "A Series")
    private val manga = SourceManga("m1", "A Series", "/a-series")

    @Test fun `missing extension carries factual count and install URL`() = runTest {
        val candidate = candidate("one", 145)
        val result = matcher(listOf(candidate)).match(series, preferences) as SmartMatchResult.MissingExtension
        assertEquals(145, result.chapterCount)
        assertEquals(candidate.extension.apkUrl, result.installUrl)
    }

    @Test fun `installed extension supplies chapters and enables reading`() = runTest {
        val candidate = candidate("one", 2)
        val result = matcher(listOf(candidate), setOf("one")).match(series, preferences) as SmartMatchResult.Ready
        assertEquals(2, result.chapters.size)
        assertTrue(result.canRead)
    }

    @Test fun `ranks chapter count first then latest date and lists alternatives`() = runTest {
        val a = candidate("one", 2, 200)
        val b = candidate("two", 3, 100)
        val c = candidate("three", 3, 300)
        val result = matcher(listOf(a, b, c), setOf("one", "two", "three")).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("three", result.selected.extension.packageName)
        assertEquals(listOf("two", "one"), result.alternatives.map { it.extension.packageName })
    }

    @Test fun `filters wrong language and adult sources before discovery`() = runTest {
        val english = candidate("english", 200, language = "en")
        val adult = candidate("adult", 200, nsfw = true)
        assertEquals(SmartMatchResult.NoMatch, matcher(listOf(english, adult)).match(series, preferences))
    }

    @Test fun `chosen language outranks a multilingual source with more chapters`() = runTest {
        val multilingual = candidate("multi", 500, language = "all")
        val turkish = candidate("turkish", 2, language = "tr")
        val result = matcher(listOf(multilingual, turkish), setOf("multi", "turkish"))
            .match(series, preferences) as SmartMatchResult.Ready
        assertEquals("turkish", result.selected.extension.packageName)
        assertEquals(listOf("multi"), result.alternatives.map { it.extension.packageName })
    }

    @Test fun `region variants of the chosen language are accepted`() = runTest {
        val regional = candidate("regional", 5, language = "tr-TR")
        val result = matcher(listOf(regional), setOf("regional")).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("regional", result.selected.extension.packageName)
    }

    @Test fun `english preference drops turkish only sources`() = runTest {
        val turkish = candidate("turkish", 200, language = "tr")
        val english = candidate("english", 1, language = "en")
        val result = matcher(listOf(turkish, english), setOf("turkish", "english"))
            .match(series, preferences.copy(language = ReadingLanguage.ENGLISH)) as SmartMatchResult.Ready
        assertEquals("english", result.selected.extension.packageName)
        assertTrue(result.alternatives.isEmpty())
    }

    @Test fun `adult preference explicitly allows adult sources`() = runTest {
        assertTrue(matcher(listOf(candidate("adult", 200, nsfw = true)))
            .match(series, preferences.copy(allowAdultContent = true)) is SmartMatchResult.MissingExtension)
    }

    @Test fun `english discovery does not query unrelated language extensions`() = runTest {
        val english = candidate("english", 3, language = "en-US")
        val multilingual = candidate("multi", 400, language = "mul")
        val turkish = candidate("turkish", 900, language = "tr")
        val repository = FakeRepository(listOf(english, multilingual, turkish).map { it.extension })
        val matcher = SmartSourceMatcher(repository, FakeBridge(emptyList()), SourceDiscovery { _, extensions ->
            assertFalse(extensions.any { it.packageName == "turkish" })
            listOf(turkish, multilingual, english) // Defensive filtering of discovery output too.
        })
        val result = matcher.match(series, ReaderPreferences(ReadingLanguage.ENGLISH)) as SmartMatchResult.MissingExtension
        assertEquals("english", result.suggestedSource.extension.packageName)
        assertEquals(listOf("multi"), result.alternatives.map { it.extension.packageName })
    }

    @Test fun `both languages ranks Turkish and English equally and excludes other languages`() = runTest {
        val turkish = candidate("turkish", 20, language = "tr")
        val english = candidate("english", 30, language = "en-US")
        val japanese = candidate("japanese", 500, language = "ja")
        val multilingual = candidate("multilingual", 600, language = "all")
        val result = matcher(listOf(turkish, english, japanese, multilingual),
            setOf("turkish", "english", "japanese", "multilingual"))
            .match(series, preferences.copy(language = ReadingLanguage.BOTH)) as SmartMatchResult.Ready
        assertEquals("english", result.selected.extension.packageName)
        assertEquals(listOf("turkish", "multilingual"), result.alternatives.map { it.extension.packageName })
    }

    @Test fun `better missing source wins over installed source`() = runTest {
        val result = matcher(listOf(candidate("one", 2), candidate("two", 145)), setOf("one"))
            .match(series, preferences) as SmartMatchResult.MissingExtension
        assertEquals("two", result.suggestedSource.extension.packageName)
        assertEquals(1, result.alternatives.size)
    }

    @Test fun `empty chapters never enable reading`() = runTest {
        val result = matcher(listOf(candidate("one", 0)), setOf("one")).match(series, preferences) as SmartMatchResult.Ready
        assertFalse(result.canRead)
    }

    @Test fun `readable multilingual fallback wins when preferred source has no chapters`() = runTest {
        val english = candidate("english", 0, language = "en")
        val multilingual = candidate("multi", 5, language = "all")
        val result = matcher(listOf(english, multilingual), setOf("english", "multi"))
            .match(series, ReaderPreferences(ReadingLanguage.ENGLISH)) as SmartMatchResult.Ready
        assertEquals("multi", result.selected.extension.packageName)
        assertTrue(result.canRead)
    }

    @Test fun `cancellation is not swallowed`() = runTest {
        val candidate = candidate("one", 3)
        val repository = FakeRepository(listOf(candidate.extension))
        val matcher = SmartSourceMatcher(repository, FakeBridge(emptyList()), SourceDiscovery { _, _ -> throw CancellationException("cancel") })
        try {
            matcher.match(series, preferences)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `catalog failure produces unavailable not no match`() = runTest {
        val candidate = candidate("one", 3)
        val result = SmartSourceMatcher(FakeRepository(listOf(candidate.extension)), FakeBridge(emptyList()),
            SourceDiscovery { _, _ -> error("offline") }).match(series, preferences)
        assertTrue(result is SmartMatchResult.Unavailable)
    }

    private fun candidate(name: String, count: Int, date: Long = 100, language: String = "tr", nsfw: Boolean = false): SourceCandidate {
        val source = ExtensionSource(name, name, language)
        return SourceCandidate(ExtensionDescriptor(name, name, language, nsfw, "1", 1, "https://example.org/$name.apk", listOf(source)), source, manga, count, date)
    }

    private fun matcher(candidates: List<SourceCandidate>, installed: Set<String> = emptySet()): SmartSourceMatcher =
        SmartSourceMatcher(FakeRepository(candidates.map { it.extension }, installed), FakeBridge(candidates),
            SourceDiscovery { _, extensions -> candidates.filter { it.extension in extensions } })

    private class FakeRepository(
        val entries: List<ExtensionDescriptor>,
        val installedPackages: Set<String> = emptySet(),
    ) : ExtensionRepository {
        override suspend fun addRepository(indexUrl: String) = Unit
        override suspend fun removeRepository(indexUrl: String) = Unit
        override suspend fun repositories() = emptyList<String>()
        override suspend fun refresh() = entries
        override suspend fun available() = entries
        override suspend fun installed() = installedPackages.map { InstalledExtension(it, 1, true) }
    }

    private class FakeBridge(val candidates: List<SourceCandidate>) : SourceBridge {
        override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int) =
            listOf(candidates.first { it.source.id == sourceId }.manga)
        override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga): List<SourceChapter> {
            val candidate = candidates.first { it.source.id == sourceId }
            return List(candidate.chapterCount) { SourceChapter("$it", "Chapter $it", it.toDouble(), candidate.latestChapterAt) }
        }
        override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter) = emptyList<SourcePage>()
    }
}
