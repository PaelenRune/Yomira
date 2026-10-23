package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import java.util.Date

/** Metadata JSON only, never chapter text, image bytes or page payloads. */
@Entity(
    tableName = "metadata_cache",
    primaryKeys = ["sourceId", "cacheKey"],
    indices = [Index("expiresAt")],
)
data class MetadataCacheEntity(
    val sourceId: String,
    val cacheKey: String,
    val metadataJson: String,
    val cachedAt: Date,
    val expiresAt: Date,
) {
    init {
        require(sourceId.isNotBlank()) { "Source ID must not be blank" }
        require(cacheKey.isNotBlank()) { "Cache key must not be blank" }
        require(expiresAt >= cachedAt) { "Cache expiry must not precede creation" }
    }
}
