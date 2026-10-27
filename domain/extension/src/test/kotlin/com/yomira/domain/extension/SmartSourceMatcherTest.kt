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

    @Test fun `only installed trusted extensions are queried and discovery is never called`() = runTest {
        val installed = candidate("installed", 2)
        val missing = candidate("missing", 1000)
        val untrusted = candidate("untrusted", 2000)
        val repo = FakeRepository(listOf(installed, missing, untrusted), setOf("installed", "untrusted"), setOf("untrusted"))
        val bridge = object : SourceBridge by FakeBridge(listOf(installed)) {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
                assertEquals("installed", packageName)
                return listOf(manga)
            }
        }
        val result = SmartSourceMatcher(repo, bridge, noDiscovery).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("installed", result.selected.extension.packageName)
        assertTrue(result.alternatives.isEmpty())
        assertEquals(0, repo.catalogReads)
    }

    @Test fun `no installations means no match even when repository advertises titles`() = runTest {
        assertEquals(SmartMatchResult.NoMatch, matcher(listOf(candidate("missing", 500)), emptySet()).match(series, preferences))
    }

    @Test fun `chapter count is primary and freshness is secondary`() = runTest {
        val values = listOf(candidate("one", 2, 900), candidate("two", 3, 100), candidate("three", 3, 300))
        val result = matcher(values).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("three", result.selected.extension.packageName)
        assertEquals(listOf("two", "one"), result.alternatives.map { it.extension.packageName })
        assertEquals(3, result.chapters.size)
        assertTrue(result.canRead)
    }

    @Test fun `old but complete source beats recent source with fewer chapters`() = runTest {
        val values = listOf(candidate("recent", 10, 1_800_000_000_000), candidate("old", 11, 100))
        val result = matcher(values).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("old", result.selected.extension.packageName)
    }

    @Test fun `unknown chapter dates sort behind known dates at equal completeness`() = runTest {
        val result = matcher(listOf(candidate("unknown", 3, null), candidate("dated", 3, 100)))
            .match(series, preferences) as SmartMatchResult.Ready
        assertEquals("dated", result.selected.extension.packageName)
    }

    @Test fun `language cannot outweigh completeness among accepted sources`() = runTest {
        val result = matcher(listOf(candidate("turkish", 1), candidate("multi", 1000, language = "all"),
            candidate("english", 500, language = "en"))).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("multi", result.selected.extension.packageName)
        assertEquals(listOf("english", "turkish"), result.alternatives.map { it.extension.packageName })
    }

    @Test fun `latency never breaks a count or freshness tie`() = runTest {
        val values = listOf(candidate("slow", 3), candidate("fast", 3))
        val ticks = listOf(0L, 10_000_000_000L, 10_000_000_000L, 10_100_000_000L).iterator()
        val result = SmartSourceMatcher(FakeRepository(values), FakeBridge(values), noDiscovery, nanoTime = { ticks.next() })
            .match(series, preferences) as SmartMatchResult.Ready
        assertEquals("slow", result.selected.extension.packageName)
    }

    @Test fun `unrelated language and adult sources are filtered before probing`() = runTest {
        assertEquals(SmartMatchResult.NoMatch, matcher(listOf(candidate("japanese", 200, language = "ja"),
            candidate("adult", 200, nsfw = true))).match(series, preferences))
        assertTrue(matcher(listOf(candidate("adult", 200, nsfw = true)))
            .match(series, preferences.copy(allowAdultContent = true)) is SmartMatchResult.Ready)
    }

    @Test fun `empty source silently falls back and all empty sources return no match`() = runTest {
        val result = matcher(listOf(candidate("empty", 0), candidate("backup", 4)))
            .match(series, preferences) as SmartMatchResult.Ready
        assertEquals("backup", result.selected.extension.packageName)
        assertTrue(result.alternatives.isEmpty())
        assertEquals(SmartMatchResult.NoMatch, matcher(listOf(candidate("empty", 0))).match(series, preferences))
    }

    @Test fun `failed chapter probe silently falls back and all failures return unavailable`() = runTest {
        val values = listOf(candidate("best", 50), candidate("backup", 4))
        val bridge = object : SourceBridge by FakeBridge(values) {
            override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga): List<SourceChapter> {
                if (packageName == "best") error("offline")
                return FakeBridge(values).getChapterList(packageName, sourceId, manga)
            }
        }
        val result = SmartSourceMatcher(FakeRepository(values), bridge, noDiscovery).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("backup", result.selected.extension.packageName)
        assertTrue(SmartSourceMatcher(FakeRepository(values, setOf("best")), bridge, noDiscovery)
            .match(series, preferences) is SmartMatchResult.Unavailable)
    }

    @Test fun `reader failover excludes failed sources and selects next installed source`() = runTest {
        val values = listOf(candidate("best", 50), candidate("backup", 40))
        val router = matcher(values)
        val first = router.match(series, preferences) as SmartMatchResult.Ready
        val next = router.failover(series, preferences, listOf(first.selected)) as SmartMatchResult.Ready
        assertEquals("backup", next.selected.extension.packageName)
        assertEquals(SmartMatchResult.NoMatch, router.failover(series, preferences, values))
    }

    @Test fun `source cancellation propagates`() = runTest {
        val values = listOf(candidate("one", 3))
        val bridge = object : SourceBridge by FakeBridge(values) {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> =
                throw CancellationException("cancelled")
        }
        try {
            SmartSourceMatcher(FakeRepository(values), bridge, noDiscovery).match(series, preferences)
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }

    @Test fun `stalled probes are bounded and do not prevent concurrent fallback`() = runTest {
        val values = listOf(candidate("stalled", 50), candidate("responsive", 5))
        var responsiveQueried = false
        val bridge = object : SourceBridge by FakeBridge(values) {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
                if (sourceId == "stalled") {
                    delay(100)
                    assertTrue(responsiveQueried)
                    delay(60_000)
                }
                responsiveQueried = true
                return listOf(manga)
            }
        }
        val result = SmartSourceMatcher(FakeRepository(values), bridge, noDiscovery).match(series, preferences) as SmartMatchResult.Ready
        assertEquals("responsive", result.selected.extension.packageName)
    }

    @Test fun `aliases are searched even after an empty title match`() = runTest {
        val found = candidate("one", 3)
        val bridge = object : SourceBridge by FakeBridge(listOf(found)) {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int) =
                listOf(if (query == "Alternate title") manga else manga.copy(id = "empty"))
            override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) =
                if (manga.id == "empty") emptyList() else FakeBridge(listOf(found)).getChapterList(packageName, sourceId, manga)
        }
        val result = SmartSourceMatcher(FakeRepository(listOf(found)), bridge, noDiscovery)
            .match(series.copy(aliases = setOf("Alternate title")), preferences) as SmartMatchResult.Ready
        assertEquals(3, result.chapters.size)
    }

    @Test fun `fuzzy spelling punctuation and editorial suffixes match installed titles`() = runTest {
        val found = candidate("one", 4).copy(manga = manga.copy(title = "Haikyuu!! (Official) (Digital) Scanlation"))
        val result = matcher(listOf(found)).match(series.copy(title = "Haikyu!!"), preferences) as SmartMatchResult.Ready
        assertEquals(4, result.chapters.size)
    }

    @Test fun `search expands Romaji punctuation and aliases`() = runTest {
        val cases = listOf(
            Triple(SeriesIdentity("one", "Haikyu!!"), "Haikyuu", "Haikyuu!!"),
            Triple(SeriesIdentity("two", "Tōkyō!"), "Toukyou", "Toukyou"),
            Triple(SeriesIdentity("three", "Attack on Titan", setOf("Shingeki no Kyojin!")), "Shingeki no Kyojin", "Shingeki no Kyojin"),
        )
        for ((identity, requiredQuery, resultTitle) in cases) {
            val found = candidate("one", 3).copy(manga = manga.copy(title = resultTitle))
            val bridge = object : SourceBridge by FakeBridge(listOf(found)) {
                override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int) =
                    if (query == requiredQuery) listOf(found.manga) else emptyList()
            }
            assertTrue(SmartSourceMatcher(FakeRepository(listOf(found)), bridge, noDiscovery)
                .match(identity, preferences) is SmartMatchResult.Ready)
        }
    }

    @Test fun `token containment tolerates subtitles and numbers but not distinct sequels`() {
        assertTrue(SmartSourceMatcher.matchesTitle("Attack on Titan Full Color Edition", listOf("Attack on Titan")))
        assertTrue(SmartSourceMatcher.matchesTitle("Series A", listOf("A Series")))
        assertTrue(SmartSourceMatcher.matchesTitle("Mob Psycho 100 2024 Vol 1", listOf("Mob Psycho 100")))
        assertTrue(SmartSourceMatcher.matchesTitle("A Series Vol. 1", listOf("A Series")))
        assertTrue(SmartSourceMatcher.matchesTitle("Naroto", listOf("Naruto")))
        assertFalse(SmartSourceMatcher.matchesTitle("A Series 2", listOf("A Series")))
        assertFalse(SmartSourceMatcher.matchesTitle("A Series 12", listOf("A Series 11")))
        assertFalse(SmartSourceMatcher.matchesTitle("Unrelated Adventures", listOf("Attack on Titan")))
    }

    @Test fun `unrelated titles and sequels never enter ranking`() = runTest {
        val unrelated = candidate("unrelated", 100).copy(manga = manga.copy(title = "Unrelated"))
        val sequel = candidate("sequel", 100).copy(manga = manga.copy(title = "A Series 2"))
        assertEquals(SmartMatchResult.NoMatch, matcher(listOf(unrelated, sequel)).match(series, preferences))
    }

    private fun candidate(name: String, count: Int, date: Long? = 100, language: String = "tr", nsfw: Boolean = false): SourceCandidate {
        val source = ExtensionSource(name, name, language)
        return SourceCandidate(ExtensionDescriptor(name, name, language, nsfw, "1", 1, "https://example.org/$name.apk", listOf(source)), source, manga, count, date)
    }

    private val noDiscovery = SourceDiscovery { _, _ -> error("Uninstalled discovery must never run") }

    private fun matcher(values: List<SourceCandidate>, installed: Set<String> = values.map { it.extension.packageName }.toSet()) =
        SmartSourceMatcher(FakeRepository(values, installed), FakeBridge(values), noDiscovery, nanoTime = { 0L })

    private class FakeRepository(
        val values: List<SourceCandidate>,
        val installedPackages: Set<String> = values.map { it.extension.packageName }.toSet(),
        val untrusted: Set<String> = emptySet(),
    ) : ExtensionRepository {
        var catalogReads = 0
        override suspend fun addRepository(indexUrl: String) = Unit
        override suspend fun removeRepository(indexUrl: String) = Unit
        override suspend fun repositories() = emptyList<String>()
        override suspend fun refresh() = values.map { it.extension }
        override suspend fun available(): List<ExtensionDescriptor> {
            catalogReads++
            error("Installed manifests do not need catalog access")
        }
        override suspend fun installed() = values.filter { it.extension.packageName in installedPackages }.map {
            InstalledExtension(it.extension.packageName, 1, it.extension.packageName !in untrusted, it.extension)
        }
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
