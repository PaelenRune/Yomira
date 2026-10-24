package com.yomira.data.metadata

import com.yomira.core.model.ContentRating
import com.yomira.core.model.ExternalRating
import com.yomira.core.network.MetadataProvider
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.RemoteMetadata
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WaterfallMetadataAggregatorTest {
    private class Cache : MetadataCache {
        var payload: String? = null
        var expiry = 0L
        override suspend fun read(key: String, nowMillis: Long) = payload?.takeIf { nowMillis < expiry }
        override suspend fun write(key: String, payload: String, nowMillis: Long, expiresAtMillis: Long) {
            this.payload = payload; expiry = expiresAtMillis
        }
    }

    private fun provider(id: String, block: suspend () -> List<RemoteMetadata>) = object : MetadataProvider {
        override val sourceId = id
        override suspend fun search(query: String, limit: Int) = block()
    }
    private fun record(source: String, title: String = "One Piece") = RemoteMetadata(source, "1", title)
    private val clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC)

    @Test fun usesCanonicalOrderAndFillsOnlyMissingFields() = runTest {
        val calls = mutableListOf<String>()
        val ids = listOf(MetadataSources.BAKA_UPDATES, MetadataSources.ANILIST, MetadataSources.MANGADEX, MetadataSources.KITSU, MetadataSources.COMICVINE)
        val providers = ids.mapIndexed { index, id -> provider(id) {
            calls += id
            listOf(record(id).copy(author = if (index == 0) "Oda" else "Do not replace",
                coverUrl = if (index == 1) "https://example.org/cover.jpg" else null,
                description = if (index == 3) "Synopsis" else null))
        } }.reversed()
        val result = WaterfallMetadataAggregator(providers, Cache(), clock = clock).aggregate("One Piece")!!
        assertEquals(ids, calls)
        assertEquals("Oda", result.author)
        assertEquals("https://example.org/cover.jpg", result.coverUrl)
        assertEquals("Synopsis", result.synopsis)
        assertEquals("${ids.first()}:1", result.id)
    }

    @Test fun failureFallsBackAndNeverMergesUnrelatedSearchHits() = runTest {
        val providers = listOf(
            provider(MetadataSources.BAKA_UPDATES) { error("offline") },
            provider(MetadataSources.ANILIST) { listOf(record(MetadataSources.ANILIST)) },
            provider(MetadataSources.MANGADEX) { listOf(record(MetadataSources.MANGADEX, "Other Series").copy(description = "Wrong")) },
        )
        val result = WaterfallMetadataAggregator(providers, Cache()).aggregate("One Piece")!!
        assertEquals(MetadataSources.ANILIST, result.sourceId)
        assertNull(result.synopsis)
    }

    @Test fun validCacheSkipsNetworkAndForceRefreshBypassesIt() = runTest {
        var calls = 0
        val cache = Cache()
        val aggregator = WaterfallMetadataAggregator(listOf(provider(MetadataSources.KITSU) { calls++; listOf(record(MetadataSources.KITSU)) }), cache, clock = clock)
        val first = aggregator.aggregate("One Piece")
        assertEquals(first, aggregator.aggregate("one piece"))
        assertEquals(1, calls)
        aggregator.aggregate("One Piece", forceRefresh = true)
        assertEquals(2, calls)
        cache.expiry = clock.millis()
        aggregator.aggregate("One Piece")
        assertEquals(3, calls)
    }

    @Test fun corruptCacheFallsBackAndConflictingRatingsStayExplicit() = runTest {
        val cache = Cache().apply { payload = "not json"; expiry = Long.MAX_VALUE }
        val providers = listOf(
            provider(MetadataSources.BAKA_UPDATES) { listOf(record(MetadataSources.BAKA_UPDATES).copy(contentRating = ExternalRating.SAFE)) },
            provider(MetadataSources.MANGADEX) { listOf(record(MetadataSources.MANGADEX).copy(contentRating = ExternalRating.PORNOGRAPHIC)) },
        )
        assertEquals(ContentRating.EXPLICIT, WaterfallMetadataAggregator(providers, cache).aggregate("One Piece")!!.contentRating)
    }

    @Test fun failedCacheDoesNotDiscardNetworkResult() = runTest {
        val cache = object : MetadataCache {
            override suspend fun read(key: String, nowMillis: Long): String? = error("disk")
            override suspend fun write(key: String, payload: String, nowMillis: Long, expiresAtMillis: Long): Unit = error("disk")
        }
        assertNotNull(WaterfallMetadataAggregator(listOf(provider(MetadataSources.KITSU) { listOf(record(MetadataSources.KITSU)) }), cache).aggregate("One Piece"))
    }

    @Test fun cancellationPropagatesInsteadOfBecomingFallback() = runTest {
        val aggregator = WaterfallMetadataAggregator(listOf(provider(MetadataSources.KITSU) { throw CancellationException("stop") }), Cache())
        try { aggregator.aggregate("One Piece"); fail("Cancellation expected") } catch (_: CancellationException) { }
    }

    @Test fun noMatchReturnsNull() = runTest {
        assertNull(WaterfallMetadataAggregator(listOf(provider(MetadataSources.KITSU) { emptyList() }), Cache()).aggregate("Unknown"))
    }

    @Test fun englishTitleBecomesDisplayTitleAndRomajiSurvivesAsAlias() = runTest {
        val providers = listOf(provider(MetadataSources.ANILIST) {
            listOf(RemoteMetadata(
                sourceId = MetadataSources.ANILIST, remoteId = "431", title = "Shingeki no Kyojin",
                altTitles = mapOf("ja" to listOf("進撃の巨人")), englishTitle = "Attack on Titan",
            ))
        })
        val manga = WaterfallMetadataAggregator(providers, Cache(), clock = clock).aggregate("Attack on Titan")!!
        // The shelf shows the known English name, never the romaji…
        assertEquals("Attack on Titan", manga.title)
        // …while every other known spelling stays searchable through the alt titles.
        assertTrue(manga.altTitles.contains("Shingeki no Kyojin"))
        assertTrue(manga.altTitles.contains("進撃の巨人"))
    }

    @Test fun recordsWithoutEnglishTitleFallBackToFirstCanonicalTitle() = runTest {
        val providers = listOf(
            provider(MetadataSources.BAKA_UPDATES) { listOf(record(MetadataSources.BAKA_UPDATES).copy(englishTitle = null)) },
            provider(MetadataSources.ANILIST) { listOf(record(MetadataSources.ANILIST)) },
        )
        val manga = WaterfallMetadataAggregator(providers, Cache(), clock = clock).aggregate("One Piece")!!
        assertEquals("One Piece", manga.title)
    }

    @Test fun duplicateCachedRecordsNeverMergeIntoTwoIdentities() = runTest {
        val cache = Cache().apply {
            payload = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(RemoteMetadata.serializer()),
                listOf(record(MetadataSources.KITSU), record(MetadataSources.KITSU)),
            )
            expiry = Long.MAX_VALUE
        }
        val manga = WaterfallMetadataAggregator(emptyList(), cache, clock = clock).aggregate("One Piece")!!
        assertEquals("${MetadataSources.KITSU}:1", manga.id)
        assertEquals("One Piece", manga.title)
    }
}
