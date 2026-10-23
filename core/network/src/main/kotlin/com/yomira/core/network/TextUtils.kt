package com.yomira.core.network

import org.jsoup.Jsoup

/** Upstream descriptions occasionally contain HTML or entities; store plain text only. */
internal fun cleanText(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val text = Jsoup.parse(raw).text().trim()
    return text.ifEmpty { null }
}

/** MangaUpdates HTML fallback uses the same cleaning rules as API descriptions. */
internal fun normalizeWhitespace(value: String?): String? =
    value?.replace(Regex("\\s+"), " ")?.trim()?.ifEmpty { null }
