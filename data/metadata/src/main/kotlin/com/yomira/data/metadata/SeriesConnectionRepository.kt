package com.yomira.data.metadata

import com.yomira.core.model.Manga
import com.yomira.core.model.SeriesConnections
import com.yomira.core.network.AniListClient
import java.text.Normalizer
import java.util.Locale

/**
 * Franchise relations and community recommendations for a series on the shelf.
 *
 * AniList is the only provider in the waterfall that exposes a structured
 * relations graph, so this repository resolves the AniList record of a series
 * first and then reads its `relations` and `recommendations`:
 *  - a card that came from AniList (`anilist:<id>`) is read directly;
 *  - every other card is matched by its display title and stored aliases,
 *    using the same normalised title identity as the waterfall aggregator.
 *
 * A series without an AniList record simply yields empty showcases — the detail
 * page treats relations and recommendations as progressive enhancement and never
 * blocks the chapter list on them.
 */
class SeriesConnectionRepository(private val aniListClient: AniListClient) {

    /** Both showcases for [manga]; empty when the series is unknown to AniList. */
    suspend fun connectionsFor(manga: Manga): SeriesConnections {
        manga.id.takeIf { it.startsWith(ANILIST_PREFIX) }?.removePrefix(ANILIST_PREFIX)
            ?.toIntOrNull()?.let { return aniListClient.connections(it) }
        for (title in (listOf(manga.title) + manga.altTitles).distinct().take(MAX_TITLE_LOOKUP)) {
            val wanted = normalize(title)
            if (wanted.isEmpty()) continue
            val match = aniListClient.search(title, SEARCH_LIMIT).firstOrNull { candidate ->
                candidate.allTitles().any { normalize(it) == wanted }
            } ?: continue
            val remoteId = match.remoteId.toIntOrNull() ?: continue
            return aniListClient.connections(remoteId)
        }
        return SeriesConnections()
    }

    /** Same title identity rule as `WaterfallMetadataAggregator`: case- and punctuation-insensitive. */
    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private companion object {
        const val ANILIST_PREFIX = "anilist:"
        const val SEARCH_LIMIT = 3
        const val MAX_TITLE_LOOKUP = 6
    }
}