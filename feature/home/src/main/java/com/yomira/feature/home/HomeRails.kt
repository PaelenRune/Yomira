package com.yomira.feature.home

import com.yomira.core.model.HomeRail
import com.yomira.core.model.HomeShelf
import com.yomira.core.model.Manga

/** Pure rail assembly, called on Default by the ViewModel, not the scrolling UI. */
fun buildHomeRails(
    catalog: List<Manga>,
    updated: List<Manga>,
    discovered: List<Manga>,
    selection: List<HomeShelf>,
    readIds: Set<String>,
    savedIds: Set<String>,
    seed: Int,
    limit: Int = 14,
): List<HomeRail> {
    val all = publicCatalog(catalog + discovered).distinctBy { it.id }
    val recent = publicCatalog(updated)
    val discoveryIds = discovered.map { it.id }.toSet()
    return selection.map { shelf ->
        val items = when (shelf) {
            HomeShelf.POPULAR -> popularTrendingShelf(all, recent, seed, limit)
            HomeShelf.PERSONAL -> personalizedShelf(all, readIds, savedIds, seed, limit)
                .ifEmpty { popularTrendingShelf(all, recent, seed, limit) }
            // There is no creation timestamp in Manga. Prefer newly fetched rows;
            // otherwise offer a stable discovery mix rather than inventing recency.
            HomeShelf.DISCOVERY -> all.sortedWith(
                compareByDescending<Manga> { it.id in discoveryIds }
                    .thenByDescending { rotationNoise(it.id, seed) }
                    .thenBy { it.id },
            ).take(limit)
            else -> all.filter(shelf::matches).sortedWith(
                compareByDescending<Manga> { it.score ?: 0.0 }
                    .thenByDescending { rotationNoise(it.id, seed) }
                    .thenBy { it.id },
            ).take(limit)
        }
        HomeRail(shelf, items)
    }
}
