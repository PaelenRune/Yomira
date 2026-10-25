package com.yomira.core.database.metadata

import com.yomira.core.database.dao.MetadataCacheDao
import com.yomira.core.database.entity.MetadataCacheEntity
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomMetadataCacheTest {
    private class Dao : MetadataCacheDao {
        var stored: MetadataCacheEntity? = null
        override suspend fun getValid(sourceId: String, cacheKey: String, now: Date) = stored?.takeIf {
            it.sourceId == sourceId && it.cacheKey == cacheKey && it.expiresAt > now
        }
        override suspend fun upsert(entry: MetadataCacheEntity) { stored = entry }
        override suspend fun deleteExpired(now: Date): Int = if (stored?.expiresAt?.let { it <= now } == true) { stored = null; 1 } else 0
        override suspend fun deleteBySource(sourceId: String) { if (stored?.sourceId == sourceId) stored = null }
        override suspend fun clear() { stored = null }
    }

    @Test fun roomAdapterUsesVersionedNamespaceAndExactExpiryBoundary() = runTest {
        val dao = Dao()
        val cache = RoomMetadataCache(dao)
        cache.write("one piece", "[]", 1000, 2000)
        assertEquals(RoomMetadataCache.SOURCE, dao.stored!!.sourceId)
        assertEquals(Date(1000), dao.stored!!.cachedAt)
        assertEquals("[]", cache.read("one piece", 1999))
        assertNull(cache.read("one piece", 2000))
        assertNull(cache.read("other title", 1500))
    }
}
