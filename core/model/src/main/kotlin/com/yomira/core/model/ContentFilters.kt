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
    "ecchi" to setOf("ecchi", "etchi", "ecchi variants"),
    "incest" to setOf("incest", "incestuous"),
    "beastiality" to setOf("beastiality", "bestiality", "zoophilia"),
    "rape" to setOf("rape", "non con", "nonconsensual"),
    "loli" to setOf("loli", "lolicon"),
    "shota" to setOf("shota", "shotacon"),
)

private val styleTags = setOf("ntr", "gore", "harem", "ecchi")
private val hardTags = setOf("futanari", "incest", "beastiality", "rape", "loli", "shota")

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
    if (genres.isEmpty()) return false
    val expanded = (excludedTags.filterNot { excluded ->
        val normalized = normalizedTag(excluded)
        styleTags.any { normalized in (TAG_ALIAS_LOOKUP[it] ?: emptySet()) || normalized == normalizedTag(blacklistTags[it].orEmpty()) }
    } + hardTags).flatMap { excluded ->
        val key = normalizedTag(excluded)
        TAG_ALIAS_LOOKUP[key] ?: setOf(key)
    }
    if (expanded.isEmpty()) return false
    val normGenres = genres.map(::normalizedTag).toSet()
    return expanded.any { it in normGenres }
}

/** Style preferences lower shelf rank without removing a title from search or details. */
fun Manga.stylePenalty(excludedTags: Set<String>): Double {
    val labels = genres.map(::normalizedTag).toSet()
    return styleTags.count { key ->
        val aliases = TAG_ALIAS_LOOKUP[key].orEmpty()
        val selected = excludedTags.any { normalizedTag(it) in aliases || normalizedTag(it) == normalizedTag(blacklistTags[key].orEmpty()) }
        selected && labels.any { it in aliases }
    } * 0.2
}

/** Unknown age is safe by default. Adult eligibility and explicit-content consent are separate. */
fun Manga.isVisibleForReader(excludedTags: Set<String>, isAdult: Boolean, allowAdultContent: Boolean): Boolean =
    !hasExcludedTags(excludedTags) && when (contentRating) {
        ContentRating.SAFE -> true
        ContentRating.MATURE -> isAdult
        ContentRating.EXPLICIT -> isAdult && allowAdultContent
    }
