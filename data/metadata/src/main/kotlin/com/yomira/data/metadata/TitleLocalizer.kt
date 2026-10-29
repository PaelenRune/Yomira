package com.yomira.data.metadata

import com.yomira.core.network.RemoteMetadata
import java.util.Locale

/** Language policy, independent of the record that owns the series id. */
object TitleLocalizer {
    fun language(code: String?): String = code.orEmpty().trim().lowercase(Locale.ROOT).replace('_', '-')

    fun isEnglish(code: String): Boolean {
        val normalized = language(code)
        // Kitsu's en_jp is Japanese romanisation, not an English translation.
        return normalized == "en" || normalized.startsWith("en-") && normalized != "en-jp"
    }

    fun isOriginal(code: String): Boolean = language(code).let {
        it.substringBefore('-') in setOf("ja", "ko", "zh") || it in setOf("romaji", "en-jp", "native")
    }

    fun select(english: List<String?>, aliases: Map<String, List<String>>, other: List<String>): String? {
        // Resolve ties deterministically too: provider iteration must not change a shelf title.
        fun Iterable<String?>.firstName() = mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .sorted().firstOrNull()
        return english.firstName()
            ?: aliases.filterKeys(::isEnglish).values.flatten().firstName()
            ?: aliases.filterKeys(::isOriginal).values.flatten().firstName()
            ?: other.firstName()
            ?: aliases.values.flatten().firstName()
    }

    fun select(records: List<RemoteMetadata>): String? = select(
        records.map { it.englishTitle },
        records.flatMap { it.altTitles.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.flatten() },
        records.map { it.title },
    )
}
