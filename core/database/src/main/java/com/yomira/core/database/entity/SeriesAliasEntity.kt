package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One row per (series, normalized alias): the series alias dictionary that the
 * waterfall aggregator fills ([com.yomira.data.metadata.SeriesAliasSink]) and
 * every name-based lookup reads.
 *
 * [normalizedAlias] is the shared normalization engine's comparison key, so a
 * lookup folds its query the same way and hits the index directly; [alias]
 * preserves the provider spelling for diagnostics and fan-out query building.
 * [lang] mirrors the provider's language code when it supplied one. This table
 * is matching evidence only — display titles stay in `manga` and are chosen by
 * the metadata waterfall, never by this dictionary.
 */
@Entity(
    tableName = "series_aliases",
    primaryKeys = ["seriesId", "normalizedAlias"],
    indices = [Index("normalizedAlias"), Index("seriesId")],
)
data class SeriesAliasEntity(
    val seriesId: String,
    val alias: String,
    val normalizedAlias: String,
    val lang: String? = null,
    val source: String,
) {
    init {
        require(seriesId.isNotBlank()) { "Series ID must not be blank" }
        require(alias.isNotBlank()) { "Alias must not be blank" }
        require(normalizedAlias.isNotBlank()) { "Normalized alias must not be blank" }
        require(source.isNotBlank()) { "Alias source must not be blank" }
    }
}
