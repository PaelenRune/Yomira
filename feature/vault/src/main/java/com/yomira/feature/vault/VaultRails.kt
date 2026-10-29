package com.yomira.feature.vault

import com.yomira.core.model.Manga
import com.yomira.core.model.globalRankScore
import com.yomira.core.model.stylePenalty

data class VaultRail(val style: String, val items: List<Manga>)

/** One isolated snapshot shared by every unlocked presentation surface. */
data class VaultHome(val collection: List<Manga>, val hero: List<Manga>, val rails: List<VaultRail>)

/** Stage counts, without titles or other potentially sensitive content. */
fun vaultDiagnostics(input: Int, explicit: Int, eligible: Int, matched: Int): String =
    "vault input=$input nonExplicit=${input - explicit} excluded=${explicit - eligible} unmatched=${eligible - matched} matched=$matched"

/** One aggregate per source and rail; never log titles or identifiers. */
fun vaultRailDiagnostics(input: List<Manga>, excludedTags: Set<String>, isAdult: Boolean, allowAdultContent: Boolean): List<String> =
    input.distinctBy { it.id }.groupBy { it.sourceId }.toSortedMap().flatMap { (source, records) ->
        val explicit = records.count { it.contentRating == com.yomira.core.model.ContentRating.EXPLICIT }
        val eligible = vaultCatalog(records, true, excludedTags = excludedTags, isAdult = isAdult, allowAdultContent = allowAdultContent)
        vaultGenreChips.map { rail ->
            val matched = vaultCatalog(eligible, true, rail).size
            "source=$source rail=$rail ${vaultDiagnostics(records.size, explicit, eligible.size, matched)}"
        }
    }

fun buildVaultHome(
    catalog: List<Manga>,
    discovered: List<Manga>,
    unlocked: Boolean,
    style: String? = null,
    railLimit: Int = 14,
    excludedTags: Set<String> = emptySet(),
): VaultHome {
    if (!unlocked) return VaultHome(emptyList(), emptyList(), emptyList())
    val all = vaultCatalog(catalog + discovered, unlocked, excludedTags = excludedTags).distinctBy { it.id }
    val collection = vaultCatalog(all, unlocked, style)
    val discoveryIds = discovered.map { it.id }.toSet()
    val maxPopularity = all.maxOfOrNull { it.popularity ?: 0L } ?: 0L
    // Discovery is the only observed freshness signal: no fabricated release dates.
    // The bonus stays small so a freshly fetched unknown can never outrank a
    // genuinely popular series on a Vault rail (v8.1.3 shelf fix).
    val ranking = compareByDescending<Manga> {
        it.globalRankScore(maxPopularity, if (it.id in discoveryIds) 0.4 else 0.0) - it.stylePenalty(excludedTags)
    }.thenBy { it.id }
    val rails = vaultGenreChips.filter { style == null || it == style }.map { genre ->
        val matches = vaultCatalog(all, unlocked, genre)
        val ordered = matches.sortedWith(ranking)
        VaultRail(genre, ordered.take(railLimit.coerceAtLeast(0)))
    }
    return VaultHome(collection, collection.sortedWith(ranking).take(6), rails)
}
