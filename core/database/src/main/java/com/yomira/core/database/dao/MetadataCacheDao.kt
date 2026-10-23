package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yomira.core.database.entity.MetadataCacheEntity
import java.util.Date

@Dao
interface MetadataCacheDao {
    // Explicit clock input makes expiration deterministic and testable.
    @Query(
        "SELECT * FROM metadata_cache " +
            "WHERE sourceId = :sourceId AND cacheKey = :cacheKey AND expiresAt > :now",
    )
    suspend fun getValid(sourceId: String, cacheKey: String, now: Date): MetadataCacheEntity?

    @Upsert
    suspend fun upsert(entry: MetadataCacheEntity)

    @Query("DELETE FROM metadata_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Date): Int

    @Query("DELETE FROM metadata_cache WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM metadata_cache")
    suspend fun clear()
}
