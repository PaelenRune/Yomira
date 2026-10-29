package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yomira.core.database.entity.SeriesAliasEntity
import kotlinx.coroutines.flow.Flow

/** Read/write access to the series alias dictionary (`series_aliases`). */
@Dao
interface SeriesAliasDao {
    /** Idempotent: the waterfall re-records aliases on every cache hit. */
    @Upsert
    suspend fun upsertAll(aliases: List<SeriesAliasEntity>)

    /**
     * The alias-dictionary match query: every series id whose dictionary
     * carries [normalizedAlias] as a folded name. Callers fold their query
     * with the shared normalization engine before asking.
     */
    @Query("SELECT DISTINCT seriesId FROM series_aliases WHERE normalizedAlias = :normalizedAlias ORDER BY seriesId")
    suspend fun findSeriesIds(normalizedAlias: String): List<String>

    /** Full rows behind a folded name, for fan-out query building. */
    @Query("SELECT * FROM series_aliases WHERE normalizedAlias = :normalizedAlias ORDER BY seriesId, alias COLLATE NOCASE")
    suspend fun findByNormalizedAlias(normalizedAlias: String): List<SeriesAliasEntity>

    @Query("SELECT * FROM series_aliases WHERE seriesId = :seriesId ORDER BY alias COLLATE NOCASE")
    suspend fun findBySeriesId(seriesId: String): List<SeriesAliasEntity>

    @Query("SELECT * FROM series_aliases WHERE seriesId = :seriesId ORDER BY alias COLLATE NOCASE")
    fun observeBySeriesId(seriesId: String): Flow<List<SeriesAliasEntity>>

    @Query("DELETE FROM series_aliases WHERE seriesId = :seriesId")
    suspend fun deleteBySeriesId(seriesId: String)

    @Query("SELECT COUNT(*) FROM series_aliases")
    suspend fun count(): Int
}
