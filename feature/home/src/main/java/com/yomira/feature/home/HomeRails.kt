package com.yomira.feature.home

import com.yomira.core.model.HomeRail
import com.yomira.core.model.HomeShelf
import com.yomira.core.model.Manga

/** Four learned-interest shelves plus one discovery slot; ties retain canonical order. */
internal fun adaptiveHomeShelves(
    catalog: List<Manga>,
    readIds: Set<String>,
    savedIds: Set<String>,
): List<HomeShelf> {
    val tasteIds = readIds + savedIds
    val taste = publicCatalog(catalog).distinctBy { it.id }.filter { it.id in tasteIds }
    val promoted = HomeShelf.dynamicPool.filterNot { it == HomeShelf.DISCOVERY }
        .sortedByDescending { shelf -> taste.count(shelf::matches) }
        .take(HomeShelf.DYNAMIC_COUNT - 1)
    return HomeShelf.fixed + promoted + HomeShelf.DISCOVERY
}

/** Pure rail assembly, called on Default by the ViewModel, not the scrolling UI. */
fun buildHomeRails(
    catalog: List<Manga>,
    updated: List<Manga>,
    discovered: List<Manga>,
    selection: List<HomeShelf>? = null,
    readIds: Set<String>,
    savedIds: Set<String>,
    seed: Int,
    limit: Int = 14,
): List<HomeRail> {
    val all = publicCatalog(catalog + discovered).distinctBy { it.id }
    val recent = publicCatalog(updated)
    val discoveryIds = discovered.map { it.id }.toSet()
    val shelves = selection ?: adaptiveHomeShelves(all, readIds, savedIds)
    return shelves.map { shelf ->
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
