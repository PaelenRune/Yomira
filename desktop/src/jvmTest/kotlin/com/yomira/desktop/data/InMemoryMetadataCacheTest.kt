package com.yomira.desktop.data

import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The desktop app has no Room database, so the shared waterfall cache contract is
 * backed by a bounded in-memory map. Its TTL boundary must match the Android
 * adapter exactly, otherwise the same query would be fresh on one target and
 * stale on the other.
 */
class InMemoryMetadataCacheTest {

    @Test
    fun `entries expire exactly at their expiry instant`() = runTest {
        val cache = InMemoryMetadataCache()
        cache.write("one piece", "[]", nowMillis = 1_000L, expiresAtMillis = 2_000L)

        assertEquals("[]", cache.read("one piece", 1_000L))
        assertEquals("[]", cache.read("one piece", 1_999L))
        assertNull("the expiry instant is already stale", cache.read("one piece", 2_000L))
        assertNull(cache.read("unrelated", 1_500L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a write must expire in the future`() = runTest {
        InMemoryMetadataCache().write("key", "{}", nowMillis = 100L, expiresAtMillis = 100L)
    }

    @Test
    fun `capacity evicts the closest expiry instead of growing without bound`() = runTest {
        val cache = InMemoryMetadataCache(maxEntries = 2)
        cache.write("short", "a", 0L, 1_000L)
        cache.write("long", "b", 0L, 9_000L)
        cache.write("medium", "c", 0L, 5_000L)

        assertNull("the shortest lived entry is evicted", cache.read("short", 10L))
        assertEquals("b", cache.read("long", 10L))
        assertEquals("c", cache.read("medium", 10L))
    }

    @Test
    fun `expired entries are reclaimed before capacity eviction`() = runTest {
        val cache = InMemoryMetadataCache(maxEntries = 2)
        cache.write("expired", "a", 0L, 100L)
        cache.write("fresh", "b", 0L, 10_000L)
        cache.write("new", "c", 1_000L, 20_000L)

        assertNull(cache.read("expired", 1_000L))
        assertEquals("b", cache.read("fresh", 1_000L))
        assertEquals("c", cache.read("new", 1_000L))
    }

    @Test
    fun `default ttl window remains cacheable`() = runTest {
        // Mirrors WaterfallMetadataAggregator's 24h TTL contract.
        val ttl = Duration.ofHours(24).toMillis()
        val cache = InMemoryMetadataCache()
        cache.write("title", "payload", 0L, ttl)
        assertEquals("payload", cache.read("title", ttl - 1))
        assertNull(cache.read("title", ttl))
        assertEquals(Clock.systemUTC().millis() > 0, true)
    }
}
