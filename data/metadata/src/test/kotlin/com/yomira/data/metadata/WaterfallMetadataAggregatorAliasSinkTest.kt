package com.yomira.data.metadata

import com.yomira.core.network.MetadataProvider
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.RemoteMetadata
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WaterfallMetadataAggregatorAliasSinkTest {
    private class Cache : MetadataCache {
        var payload: String? = null
        var expiry = 0L
        override suspend fun read(key: String, nowMillis: Long) = payload?.takeIf { nowMillis < expiry }
        override suspend fun write(key: String, payload: String, nowMillis: Long, expiresAtMillis: Long) {
            this.payload = payload; expiry = expiresAtMillis
        }
        override suspend fun pruneExpired(nowMillis: Long) {}
    }

    private class RecordingSink : SeriesAliasSink {
        val recorded = mutableListOf<SeriesAlias>()
        var fail = false
        override suspend fun record(aliases: List<SeriesAlias>) {
            if (fail) error("dictionary unavailable")
            recorded += aliases
        }
    }

    private fun provider(id: String, result: RemoteMetadata) = object : MetadataProvider {
        override val sourceId = id
        override suspend fun search(query: String, limit: Int) = listOf(result)
    }

    private val clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC)

    private val record = RemoteMetadata(
        MetadataSources.BAKA_UPDATES, "42", "Shingeki no Kyojin",
        englishTitle = "Attack on Titan",
        altTitles = mapOf("ja" to listOf("進撃の巨人"), "en-jp" to listOf("Shingeki no Kyojin")),
    )

    @Test fun `fresh aggregation records every observed title with language and source`() = runTest {
        val sink = RecordingSink()
        val aggregator = WaterfallMetadataAggregator(
            listOf(provider(MetadataSources.BAKA_UPDATES, record)), Cache(), clock = clock, aliasSink = sink,
        )
        assertNotNull(aggregator.aggregate("Attack on Titan"))
        val expected = listOf(
            SeriesAlias("${MetadataSources.BAKA_UPDATES}:42", "Shingeki no Kyojin", null, MetadataSources.BAKA_UPDATES),
            SeriesAlias("${MetadataSources.BAKA_UPDATES}:42", "Attack on Titan", "en", MetadataSources.BAKA_UPDATES),
            SeriesAlias("${MetadataSources.BAKA_UPDATES}:42", "進撃の巨人", "ja", MetadataSources.BAKA_UPDATES),
            SeriesAlias("${MetadataSources.BAKA_UPDATES}:42", "Shingeki no Kyojin", "en-jp", MetadataSources.BAKA_UPDATES),
        )
        assertEquals(expected, sink.recorded)
    }

    @Test fun `cache hits re-record so a dictionary rebuilt after data loss self-heals`() = runTest {
        val sink = RecordingSink()
        val aggregator = WaterfallMetadataAggregator(
            listOf(provider(MetadataSources.BAKA_UPDATES, record)), Cache(), clock = clock, aliasSink = sink,
        )
        aggregator.aggregate("Attack on Titan")
        assertEquals(4, sink.recorded.size)
        aggregator.aggregate("attack on titan")
        // Same folded cache key: the second call serves from cache and re-records.
        assertEquals(8, sink.recorded.size)
    }

    @Test fun `a failing sink never breaks aggregation`() = runTest {
        val sink = RecordingSink().apply { fail = true }
        val aggregator = WaterfallMetadataAggregator(
            listOf(provider(MetadataSources.BAKA_UPDATES, record)), Cache(), clock = clock, aliasSink = sink,
        )
        val result = aggregator.aggregate("Attack on Titan")
        assertNotNull(result)
        assertEquals("Attack on Titan", result!!.title)
    }

    @Test fun `no sink configured keeps aggregation working unchanged`() = runTest {
        val aggregator = WaterfallMetadataAggregator(
            listOf(provider(MetadataSources.BAKA_UPDATES, record)), Cache(), clock = clock,
        )
        assertEquals("Attack on Titan", aggregator.aggregate("Attack on Titan")!!.title)
    }

    @Test fun `records without alt titles still record canonical and english names`() = runTest {
        val sink = RecordingSink()
        val plain = RemoteMetadata(MetadataSources.KITSU, "7", "One Piece")
        val aggregator = WaterfallMetadataAggregator(
            listOf(provider(MetadataSources.KITSU, plain)), Cache(), clock = clock, aliasSink = sink,
        )
        assertNotNull(aggregator.aggregate("One Piece"))
        assertEquals(listOf(SeriesAlias("${MetadataSources.KITSU}:7", "One Piece", null, MetadataSources.KITSU)), sink.recorded)
    }
}
