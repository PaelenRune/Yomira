package com.yomira.core.model

import java.text.Normalizer
import java.util.Locale

/** Stable preference keys; provider spelling and punctuation are normalized at the boundary. */
val blacklistTags = linkedMapOf(
    "yaoi" to "Yaoi / BL", "yuri" to "Yuri / GL", "futanari" to "Futanari",
    "ntr" to "Netorare (NTR)", "gore" to "Gore / Aşırı Şiddet", "harem" to "Harem",
)

private val COMBINING_MARKS_REGEX = Regex("\\p{M}+")
private val NON_ALPHANUM_REGEX = Regex("[^\\p{L}\\p{N}]+")

fun normalizedTag(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(COMBINING_MARKS_REGEX, "").lowercase(Locale.ROOT).replace('ı', 'i')
    .replace(NON_ALPHANUM_REGEX, " ").trim()

private val tagAliases = mapOf(
    "yaoi" to setOf("yaoi", "bl", "boys love", "boy love", "shounen ai", "shonen ai"),
    "yuri" to setOf("yuri", "gl", "girls love", "girl love", "shoujo ai", "shojo ai"),
    "futanari" to setOf("futanari", "futa"),
    "ntr" to setOf("ntr", "netorare"),
    "gore" to setOf("gore", "guro", "extreme violence", "graphic violence", "asiri siddet"),
    "harem" to setOf("harem", "reverse harem"),
)

private val TAG_ALIAS_LOOKUP: Map<String, Set<String>> = buildMap {
    tagAliases.forEach { (id, aliases) ->
        val normId = normalizedTag(id)
        val normAliases = (aliases + id).map { normalizedTag(it) }.toSet()
        put(normId, normAliases)
        blacklistTags[id]?.let { put(normalizedTag(it), normAliases) }
        normAliases.forEach { put(it, normAliases) }
    }
}

fun Manga.hasExcludedTags(excludedTags: Set<String>): Boolean {
    if (excludedTags.isEmpty() || genres.isEmpty()) return false
    val expanded = excludedTags.flatMap { excluded ->
        val key = normalizedTag(excluded)
        TAG_ALIAS_LOOKUP[key] ?: setOf(key)
    }
    if (expanded.isEmpty()) return false
    val normGenres = genres.map { " ${normalizedTag(it)} " }
    return expanded.any { alias ->
        val target = " $alias "
        normGenres.any { target in it }
    }
}

/** Unknown age is safe by default. Adult eligibility and explicit-content consent are separate. */
fun Manga.isVisibleForReader(excludedTags: Set<String>, isAdult: Boolean, allowAdultContent: Boolean): Boolean =
    !hasExcludedTags(excludedTags) && when (contentRating) {
        ContentRating.SAFE -> true
        ContentRating.MATURE -> isAdult
        ContentRating.EXPLICIT -> isAdult && allowAdultContent
    }
