package com.yomira.feature.home

import com.yomira.core.model.HomeRail
import com.yomira.core.model.HomeShelf
import com.yomira.core.model.Manga
import com.yomira.core.model.globalRankScore
import com.yomira.domain.extension.FavoriteSeries

/** Four learned-interest shelves plus one discovery slot; ties retain canonical order. */
internal fun adaptiveHomeShelves(
    catalog: List<Manga>,
    readIds: Set<String>,
    savedIds: Set<String>,
    isAdult: Boolean = false,
): List<HomeShelf> {
    val tasteIds = readIds + savedIds
    val taste = publicCatalog(catalog, isAdult = isAdult).distinctBy { it.id }.filter { it.id in tasteIds }
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
    favoriteSeries: List<FavoriteSeries> = emptyList(),
    legacyFavoriteGenres: Set<String> = emptySet(),
    isAdult: Boolean = false,
): List<HomeRail> {
    val all = publicCatalog(catalog + discovered, isAdult = isAdult).distinctBy { it.id }
    val recent = publicCatalog(updated, isAdult = isAdult)
    val discoveryIds = discovered.map { it.id }.toSet()
    val shelves = selection ?: adaptiveHomeShelves(all, readIds, savedIds, isAdult)
    return shelves.map { shelf ->
        val items = when (shelf) {
            HomeShelf.POPULAR -> popularTrendingShelf(all, recent, seed, limit)
            HomeShelf.PERSONAL -> personalizedShelf(all, readIds, savedIds, seed, limit, favoriteSeries, legacyFavoriteGenres)
                .ifEmpty { popularTrendingShelf(all, recent, seed, limit) }
            // There is no creation timestamp in Manga. Prefer newly fetched rows,
            // but rank those by real reader consensus (v8.1.3): a fresh fetch of an
            // obscure title must not beat a fresh fetch of a global hit. The
            // leftover slots keep the stable discovery mix rather than inventing recency.
            HomeShelf.DISCOVERY -> {
                val maxPopularity = all.maxOfOrNull { it.popularity ?: 0L } ?: 0L
                all.sortedWith(
                    compareByDescending<Manga> { it.id in discoveryIds }
                        .thenByDescending { manga ->
                            if (manga.id in discoveryIds) manga.globalRankScore(maxPopularity) else 0.0
                        }
                        .thenByDescending { rotationNoise(it.id, seed) }
                        .thenBy { it.id },
                ).take(limit)
            }
            else -> all.filter(shelf::matches).sortedWith(
                compareByDescending<Manga> { it.score ?: 0.0 }
                    .thenByDescending { rotationNoise(it.id, seed) }
                    .thenBy { it.id },
            ).take(limit)
        }
        HomeRail(shelf, items)
    }
}
