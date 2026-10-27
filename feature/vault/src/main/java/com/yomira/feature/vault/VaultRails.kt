package com.yomira.feature.vault

import com.yomira.core.model.Manga
import com.yomira.core.model.globalRankScore

data class VaultRail(val style: String, val items: List<Manga>)

/** One isolated snapshot shared by every unlocked presentation surface. */
data class VaultHome(val collection: List<Manga>, val hero: List<Manga>, val rails: List<VaultRail>)

fun buildVaultHome(
    catalog: List<Manga>,
    discovered: List<Manga>,
    unlocked: Boolean,
    style: String? = null,
    railLimit: Int = 14,
): VaultHome {
    if (!unlocked) return VaultHome(emptyList(), emptyList(), emptyList())
    val all = vaultCatalog(catalog + discovered, unlocked).distinctBy { it.id }
    val collection = vaultCatalog(all, unlocked, style)
    val discoveryIds = discovered.map { it.id }.toSet()
    val maxPopularity = all.maxOfOrNull { it.popularity ?: 0L } ?: 0L
    // Discovery is the only observed freshness signal: no fabricated release dates.
    val ranking = compareByDescending<Manga> {
        it.globalRankScore(maxPopularity, if (it.id in discoveryIds) 1.0 else 0.0)
    }.thenBy { it.id }
    val rails = vaultGenreChips.filter { style == null || it == style }.map { genre ->
        val matches = vaultCatalog(all, unlocked, genre)
        val ordered = matches.sortedWith(ranking)
        VaultRail(genre, ordered.take(railLimit.coerceAtLeast(0)))
    }
    return VaultHome(collection, collection.sortedWith(ranking).take(6), rails)
}
