package com.yomira.core.database.metadata

import com.yomira.core.database.dao.SeriesAliasDao
import com.yomira.core.database.entity.SeriesAliasEntity
import com.yomira.data.metadata.SeriesAlias
import com.yomira.data.metadata.SeriesAliasSink
import com.yomira.domain.extension.TitleNormalizer
import java.util.Locale

/**
 * Android persistence adapter for the series alias dictionary. Folds every
 * incoming alias through the shared [TitleNormalizer] so the dictionary's
 * `normalizedAlias` column is queryable with the exact same key a lookup
 * computes; the raw provider spelling is kept beside it for diagnostics and
 * fan-out query building. Idempotent by upsert, and one series never stores
 * the same folded name twice.
 */
class RoomSeriesAliasSink(private val dao: SeriesAliasDao) : SeriesAliasSink {
    override suspend fun record(aliases: List<SeriesAlias>) {
        if (aliases.isEmpty()) return
        // Later evidence wins: within one aggregation the localized alt-title
        // arrives after the canonical name and carries the richer language.
        val folded = LinkedHashMap<Pair<String, String>, SeriesAliasEntity>(aliases.size)
        for (alias in aliases) {
            val entity = toEntity(alias)
            folded[entity.seriesId to entity.normalizedAlias] = entity
        }
        dao.upsertAll(folded.values.toList())
    }

    private fun toEntity(alias: SeriesAlias): SeriesAliasEntity = SeriesAliasEntity(
        seriesId = alias.seriesId,
        alias = alias.title,
        normalizedAlias = TitleNormalizer.canonical(alias.title),
        lang = alias.lang?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty),
        source = alias.source,
    )
}
