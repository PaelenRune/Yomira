package com.yomira.core.model

import java.text.Normalizer
import java.util.Locale

/** Stable preference keys; provider spelling and punctuation are normalized at the boundary. */
val blacklistTags = linkedMapOf(
    "yaoi" to "Yaoi / BL", "yuri" to "Yuri / GL", "futanari" to "Futanari",
    "ntr" to "Netorare (NTR)", "gore" to "Gore / Aşırı Şiddet", "harem" to "Harem",
)

private fun normalizedTag(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('ı', 'i')
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private val tagAliases = mapOf(
    "yaoi" to setOf("yaoi", "bl", "boys love", "boy love", "shounen ai", "shonen ai"),
    "yuri" to setOf("yuri", "gl", "girls love", "girl love", "shoujo ai", "shojo ai"),
    "futanari" to setOf("futanari", "futa"),
    "ntr" to setOf("ntr", "netorare"),
    "gore" to setOf("gore", "guro", "extreme violence", "graphic violence", "asiri siddet"),
    "harem" to setOf("harem", "reverse harem"),
)

fun Manga.hasExcludedTags(excludedTags: Set<String>): Boolean = excludedTags.any { excluded ->
    val key = normalizedTag(excluded)
    val aliases = tagAliases.entries.firstOrNull { (id, values) ->
        key == id || key in values || normalizedTag(blacklistTags[id].orEmpty()) == key
    }?.value ?: setOf(key)
    genres.any { genre -> aliases.any { alias -> " $alias " in " ${normalizedTag(genre)} " } }
}

/** Unknown age is safe by default. Adult eligibility and explicit-content consent are separate. */
fun Manga.isVisibleForReader(excludedTags: Set<String>, isAdult: Boolean, allowAdultContent: Boolean): Boolean =
    !hasExcludedTags(excludedTags) && when (contentRating) {
        ContentRating.SAFE -> true
        ContentRating.MATURE -> isAdult
        ContentRating.EXPLICIT -> isAdult && allowAdultContent
    }
