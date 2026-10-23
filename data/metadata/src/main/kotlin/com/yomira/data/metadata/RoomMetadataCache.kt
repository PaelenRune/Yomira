package com.yomira.data.metadata

import com.yomira.core.database.dao.MetadataCacheDao
import com.yomira.core.database.entity.MetadataCacheEntity
import java.util.Date

class RoomMetadataCache(private val dao: MetadataCacheDao) : MetadataCache {
    override suspend fun read(key: String, nowMillis: Long): String? =
        dao.getValid(SOURCE, key, Date(nowMillis))?.metadataJson

    override suspend fun write(key: String, payload: String, nowMillis: Long, expiresAtMillis: Long) {
        dao.upsert(MetadataCacheEntity(SOURCE, key, payload, Date(nowMillis), Date(expiresAtMillis)))
    }

    companion object { const val SOURCE = "waterfall-v1" }
}
