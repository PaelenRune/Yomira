package com.yomira.core.database.metadata

import com.yomira.core.database.dao.MetadataCacheDao
import com.yomira.core.database.entity.MetadataCacheEntity
import com.yomira.data.metadata.MetadataCache
import java.util.Date

/**
 * Android persistence adapter for the shared, platform-neutral metadata cache
 * contract. The waterfall itself lives in the pure JVM `:data:metadata` module so
 * the same caching behaviour is available to the Windows desktop target.
 */
class RoomMetadataCache(private val dao: MetadataCacheDao) : MetadataCache {
    override suspend fun read(key: String, nowMillis: Long): String? =
        dao.getValid(SOURCE, key, Date(nowMillis))?.metadataJson

    override suspend fun write(key: String, payload: String, nowMillis: Long, expiresAtMillis: Long) {
        dao.upsert(MetadataCacheEntity(SOURCE, key, payload, Date(nowMillis), Date(expiresAtMillis)))
    }

    companion object { const val SOURCE = "waterfall-v1" }
}
