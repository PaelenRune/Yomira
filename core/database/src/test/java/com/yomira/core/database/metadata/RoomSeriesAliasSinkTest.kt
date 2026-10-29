package com.yomira.core.database.metadata

import com.yomira.core.database.dao.SeriesAliasDao
import com.yomira.core.database.entity.SeriesAliasEntity
import com.yomira.data.metadata.SeriesAlias
import com.yomira.domain.extension.TitleNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomSeriesAliasSinkTest {
    private class Dao : SeriesAliasDao {
        val stored = mutableListOf<SeriesAliasEntity>()
        override suspend fun upsertAll(aliases: List<SeriesAliasEntity>) {
            aliases.forEach { incoming ->
                stored.removeAll { it.seriesId == incoming.seriesId && it.normalizedAlias == incoming.normalizedAlias }
                stored += incoming
            }
        }
        override suspend fun findSeriesIds(normalizedAlias: String) =
            stored.filter { it.normalizedAlias == normalizedAlias }.map { it.seriesId }.distinct()
        override suspend fun findByNormalizedAlias(normalizedAlias: String) =
            stored.filter { it.normalizedAlias == normalizedAlias }
        override suspend fun findBySeriesId(seriesId: String) = stored.filter { it.seriesId == seriesId }
        override fun observeBySeriesId(seriesId: String) = kotlinx.coroutines.flow.flowOf(stored.filter { it.seriesId == seriesId })
        override suspend fun deleteBySeriesId(seriesId: String) { stored.removeAll { it.seriesId == seriesId } }
        override suspend fun count() = stored.size
    }

    @Test fun `aliases fold through the shared normalizer and keep raw spellings`() = runTest {
        val dao = Dao()
        RoomSeriesAliasSink(dao).record(listOf(
            SeriesAlias("baka-updates:42", "Shingeki no Kyojin", "en-jp", "baka-updates"),
            SeriesAlias("baka-updates:42", "Attack on Titan: Full Color Edition", "en", "baka-updates"),
        ))
        assertEquals(2, dao.stored.size)
        val folded = dao.findByNormalizedAlias(TitleNormalizer.canonical("Shingeki no Kyojin"))
        assertEquals("baka-updates:42", folded.single().seriesId)
        assertEquals("Shingeki no Kyojin", folded.single().alias)
        // Editorial noise never enters the dictionary key.
        assertTrue(dao.findByNormalizedAlias(TitleNormalizer.canonical("Attack on Titan")).isNotEmpty())
        assertEquals(0, dao.findByNormalizedAlias("attack on titan full color edition").size)
    }

    @Test fun `one series never stores the same folded name twice`() = runTest {
        val dao = Dao()
        RoomSeriesAliasSink(dao).record(listOf(
            SeriesAlias("kitsu:7", "Naruto Shippuuden", null, "kitsu"),
            SeriesAlias("kitsu:7", "Naruto Shippūden", "ja", "kitsu"),
        ))
        assertEquals(1, dao.findBySeriesId("kitsu:7").size)
        // The later row wins and keeps its language evidence.
        assertEquals("ja", dao.findBySeriesId("kitsu:7").single().lang)
    }

    @Test fun `the match query resolves a series id from any folded name`() = runTest {
        val dao = Dao()
        RoomSeriesAliasSink(dao).record(listOf(
            SeriesAlias("baka-updates:42", "進撃の巨人", "ja", "baka-updates"),
            SeriesAlias("baka-updates:42", "Attack on Titan", "en", "baka-updates"),
            SeriesAlias("kitsu:7", "One Piece", "en", "kitsu"),
        ))
        assertEquals(listOf("baka-updates:42"), dao.findSeriesIds(TitleNormalizer.canonical("Attack on Titan")))
        assertEquals(listOf("baka-updates:42"), dao.findSeriesIds(TitleNormalizer.canonical("進撃の巨人")))
        assertEquals(emptyList<String>(), dao.findSeriesIds(TitleNormalizer.canonical("One Punch Man")))
    }

    @Test fun `blank aliases are rejected by the entity contract and never recorded`() = runTest {
        val dao = Dao()
        runCatching {
            RoomSeriesAliasSink(dao).record(listOf(SeriesAlias("kitsu:7", "  ", null, "kitsu")))
        }
        // The aggregator filters blanks before calling the sink; a stray blank
        // must still not produce a stored row here.
        assertEquals(0, dao.count())
    }
}
