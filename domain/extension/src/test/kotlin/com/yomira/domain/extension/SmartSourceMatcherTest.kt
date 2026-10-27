package com.yomira.domain.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

    @Test fun `ranks equally healthy sources by completeness then latest date`() = runTest {
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

    @Test fun `installed healthy source beats a marginally larger missing source`() = runTest {
        val result = matcher(listOf(candidate("installed", 20), candidate("missing", 21)), setOf("installed"))
            .match(series, preferences) as SmartMatchResult.Ready
        assertEquals("installed", result.selected.extension.packageName)
    }

    @Test fun `recent chapters improve quality within the chosen language`() = runTest {
        val now = 1_800_000_000_000L
        val recent = candidate("recent", 10, now)
        val old = candidate("old", 11, now - 90L * 86_400_000L)
        val result = SmartSourceMatcher(FakeRepository(listOf(recent.extension, old.extension)),
            FakeBridge(emptyList()), SourceDiscovery { _, _ -> listOf(old, recent) }, currentTimeMillis = { now })
            .match(series, preferences) as SmartMatchResult.MissingExtension
        assertEquals("recent", result.suggestedSource.extension.packageName)
    }

    @Test fun `fast healthy source receives a bounded response time bonus`() = runTest {
        val slow = candidate("slow", 10)
        val fast = candidate("fast", 10)
        val ticks = listOf(0L, 10_000_000_000L, 10_000_000_000L, 10_100_000_000L).iterator()
        val result = SmartSourceMatcher(FakeRepository(listOf(slow.extension, fast.extension), setOf("slow", "fast")),
            FakeBridge(listOf(slow, fast)), SourceDiscovery { _, _ -> emptyList() }, nanoTime = { ticks.next() })
            .match(series, preferences) as SmartMatchResult.Ready
        assertEquals("fast", result.selected.extension.packageName)
    }

    @Test fun `language tier cannot be outweighed by multilingual completeness`() = runTest {
        val result = matcher(listOf(candidate("multi", 100_000, language = "multi"), candidate("turkish", 1)))
            .match(series, preferences) as SmartMatchResult.MissingExtension
        assertEquals("turkish", result.suggestedSource.extension.packageName)
    }

    @Test fun `discovery cannot supply unrelated series or empty multilingual fallbacks`() = runTest {
        val wrong = candidate("wrong", 100, language = "all").copy(manga = manga.copy(title = "Unrelated"))
        val empty = candidate("empty", 0, language = "mul")
        assertEquals(SmartMatchResult.NoMatch, matcher(listOf(wrong, empty)).match(series, preferences))
    }

    @Test fun `installed source searches aliases before giving up`() = runTest {
        val found = candidate("one", 2)
        val bridge = object : SourceBridge by FakeBridge(listOf(found)) {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int) =
                if (query == "Alternate title") listOf(manga) else emptyList()
        }
        val result = SmartSourceMatcher(FakeRepository(listOf(found.extension), setOf("one")), bridge,
            SourceDiscovery { _, _ -> emptyList() })
            .match(series.copy(aliases = setOf("Alternate title")), preferences) as SmartMatchResult.Ready
        assertEquals("one", result.selected.extension.packageName)
        assertEquals(2, result.chapters.size)
    }

    @Test fun `fuzzy spelling punctuation and stacked editorial suffixes match in both paths`() = runTest {
        val identity = series.copy(title = "Haikyu!!")
        val found = candidate("arbitrary-source", 4).copy(manga = manga.copy(title = "Haikyuu!! (Official) (Digital) Scanlation"))
        val missing = matcher(listOf(found)).match(identity, preferences) as SmartMatchResult.MissingExtension
        assertEquals(found, missing.suggestedSource)
        val ready = matcher(listOf(found), setOf("arbitrary-source")).match(identity, preferences) as SmartMatchResult.Ready
        assertEquals(4, ready.chapters.size)
    }

    @Test fun `token order variation matches but a distinct sequel does not`() = runTest {
        val reordered = candidate("reordered", 3).copy(manga = manga.copy(title = "Series A"))
        val sequel = candidate("sequel", 30).copy(manga = manga.copy(title = "A Series 2"))
        val result = matcher(listOf(sequel, reordered)).match(series, preferences) as SmartMatchResult.MissingExtension
        assertEquals("reordered", result.suggestedSource.extension.packageName)
        assertTrue(result.alternatives.isEmpty())
    }

    @Test fun `failed chapter probe silently falls back to another site`() = runTest {
        val best = candidate("previously-best", 50)
        val backup = candidate("new-site", 4)
        val bridge = object : SourceBridge by FakeBridge(listOf(best, backup)) {
            override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga): List<SourceChapter> {
                if (sourceId == best.source.id) error("Blocked")
                return FakeBridge(listOf(backup)).getChapterList(packageName, sourceId, manga)
            }
        }
        val result = SmartSourceMatcher(FakeRepository(listOf(best.extension, backup.extension), setOf("previously-best", "new-site")),
            bridge, SourceDiscovery { _, _ -> emptyList() }).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("new-site", result.selected.extension.packageName)
    }

    @Test fun `reader failover excludes all failed sources and reraces remaining sites`() = runTest {
        val best = candidate("best", 50)
        val backup = candidate("backup", 40)
        val router = matcher(listOf(best, backup), setOf("best", "backup"))
        val first = router.match(series, preferences) as SmartMatchResult.Ready
        val next = router.failover(series, preferences, listOf(first.selected)) as SmartMatchResult.Ready
        assertEquals("backup", next.selected.extension.packageName)
        assertEquals(SmartMatchResult.NoMatch, router.failover(series, preferences, listOf(best, backup)))
    }

    @Test fun `blocked discovery source is excluded and observed health earns bounded bonus`() = runTest {
        val blocked = candidate("blocked", 100).copy(health = SourceHealth.BLOCKED)
        val healthy = candidate("healthy", 19).copy(health = SourceHealth.HEALTHY)
        val unknown = candidate("unknown", 20)
        val result = matcher(listOf(blocked, unknown, healthy)).match(series, preferences) as SmartMatchResult.MissingExtension
        assertEquals("healthy", result.suggestedSource.extension.packageName)
        assertEquals(listOf("unknown"), result.alternatives.map { it.extension.packageName })
    }

    @Test fun `completeness is a bounded ratio rather than raw chapter count`() = runTest {
        val healthy = candidate("healthy", 99_000).copy(health = SourceHealth.HEALTHY)
        val unknown = candidate("unknown", 100_000)
        val result = matcher(listOf(unknown, healthy)).match(series, preferences) as SmartMatchResult.MissingExtension
        assertEquals("healthy", result.suggestedSource.extension.packageName)
    }

    @Test fun `runtime probes race concurrently and a stalled site cannot prevent fallback`() = runTest {
        val slow = candidate("stalled", 50)
        val fast = candidate("responsive", 5)
        var fastWasQueried = false
        val bridge = object : SourceBridge by FakeBridge(listOf(slow, fast)) {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
                if (sourceId == "stalled") {
                    delay(100)
                    assertTrue(fastWasQueried)
                    delay(60_000)
                }
                fastWasQueried = true
                return listOf(manga)
            }
        }
        val result = SmartSourceMatcher(FakeRepository(listOf(slow.extension, fast.extension), setOf("stalled", "responsive")),
            bridge, SourceDiscovery { _, _ -> emptyList() }).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("responsive", result.selected.extension.packageName)
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
