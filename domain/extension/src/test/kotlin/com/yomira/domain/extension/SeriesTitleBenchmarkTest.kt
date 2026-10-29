package com.yomira.domain.extension

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 100-series benchmark harness (`.orchestrator/content/matcher/testset-100.json`,
 * copied to test resources). For every series the benchmark issues its EN,
 * original and alias names as lookup queries against all 100 series — each
 * resolved through the full name set — and requires the best scoring series
 * to be the query's own canonical id, at or above [TitleMatcher.MATCH_THRESHOLD].
 *
 * The legacy engine (the private matcher that lived inside SmartSourceMatcher
 * at v8.2.3, ported verbatim below) is measured on the same queries for the
 * BEFORE/AFTER report. Ties resolve to the lowest series id in both engines,
 * which is exactly the "first row wins" behaviour of an unordered DB scan.
 */
class SeriesTitleBenchmarkTest {

    private class Entry(
        val id: Int,
        val titleEn: String,
        val original: String?,
        val romaji: String?,
        val aliases: List<String>,
        val difficulty: String,
        val confidence: String,
    ) {
        /** Subject names: everything known about the series. */
        val subjectNames: List<String>
            get() = listOfNotNull(titleEn, original, romaji) + aliases

        /** Query names: EN + original + aliases (romaji is not queried). */
        val queryNames: List<String>
            get() = listOfNotNull(titleEn, original) + aliases
    }

    private fun loadEntries(): List<Entry> {
        val stream = javaClass.classLoader.getResourceAsStream("matcher/testset-100.json")
            ?: error("testset-100.json is missing from test resources")
        val text = stream.readBytes().decodeToString()
        return Json.parseToJsonElement(text).jsonArray.map { element ->
            val obj = element.jsonObject
            fun string(key: String): String? = obj[key]?.jsonPrimitive?.content
            Entry(
                id = string("id")!!.toInt(),
                titleEn = string("titleEn")!!,
                original = obj["originalTitle"]?.jsonObject?.get("text")?.jsonPrimitive?.content,
                romaji = string("romaji"),
                aliases = obj["aliases"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                difficulty = string("difficulty") ?: "orta",
                confidence = string("confidence") ?: "high",
            )
        }.sortedBy { it.id }
    }

    @Test fun `unified engine resolves at least 90 percent of en original and alias queries`() {
        val entries = loadEntries()
        val subjects = entries.associate { it.id to TitleMatcher.Subject(it.subjectNames) }
        val legacyNames = entries.associate { it.id to LegacyEngine.names(it.subjectNames) }

        var newResolved = 0
        var legacyResolved = 0
        var total = 0
        val newMisses = mutableListOf<String>()
        val legacyMisses = mutableListOf<String>()
        val hardQueries = mutableListOf<Boolean>() // new-engine resolution per zor/low query

        for (entry in entries) {
            for (query in entry.queryNames) {
                if (query.isBlank()) continue
                total++
                val newBest = best(entries, subjects, legacyNames, query, legacy = false)
                val legacyBest = best(entries, subjects, legacyNames, query, legacy = true)
                val newOk = newBest == entry.id
                val legacyOk = legacyBest == entry.id
                if (newOk) newResolved++ else newMisses += "#${entry.id} «$query»"
                if (legacyOk) legacyResolved++ else legacyMisses += "#${entry.id} «$query»"
                if (entry.difficulty == "zor" || entry.confidence == "low") hardQueries += newOk
            }
        }

        val newRate = newResolved.toDouble() / total
        val legacyRate = legacyResolved.toDouble() / total
        val hardTotal = hardQueries.size
        val hardResolved = hardQueries.count { it }
        val hardRate = if (hardTotal == 0) 1.0 else hardResolved.toDouble() / hardTotal

        println("BENCHMARK total queries: $total")
        println("BENCHMARK BEFORE (v8.2.3 legacy engine): ${"%.1f".format(legacyRate * 100)}% ($legacyResolved/$total)")
        println("BENCHMARK AFTER  (unified engine):      ${"%.1f".format(newRate * 100)}% ($newResolved/$total)")
        println("BENCHMARK hard subset (zor/low):        ${"%.1f".format(hardRate * 100)}% ($hardResolved/$hardTotal)")
        println("BENCHMARK new-engine misses: ${newMisses.joinToString()}")
        println("BENCHMARK legacy misses: ${legacyMisses.joinToString()}")

        assertTrue("Unified engine resolved only ${"%.1f".format(newRate * 100)}% (< 90%)", newRate >= 0.90)
        assertTrue("Testset must stay meaningful (100 entries)", entries.size == 100)
    }

    /** First (lowest-id) series wins ties, in both engines. */
    private fun best(
        entries: List<Entry>,
        subjects: Map<Int, TitleMatcher.Subject>,
        legacyNames: Map<Int, Set<String>>,
        query: String,
        legacy: Boolean,
    ): Int? {
        var bestId: Int? = null
        var bestScore = TitleMatcher.MATCH_THRESHOLD
        for (entry in entries) {
            val score = if (legacy) {
                val names = legacyNames.getValue(entry.id)
                if (names.isEmpty()) 0.0 else names.maxOf { LegacyEngine.similarity(LegacyEngine.normalize(query), it) }
            } else {
                TitleMatcher.score(query, subjects.getValue(entry.id))
            }
            if (score > bestScore) {
                bestScore = score
                bestId = entry.id
            }
        }
        return bestId
    }

    /**
     * Verbatim port of the private matcher inside SmartSourceMatcher at
     * v8.2.3 (09ff273): the BEFORE side of the report.
     */
    private object LegacyEngine {
        fun names(titles: List<String>): Set<String> =
            expandedQueries(titles).map(::normalize).toSet().filter { it.isNotEmpty() }.toSet()

        private fun expandedQueries(titles: Collection<String>): List<String> = titles.flatMap { title ->
            val romanized = title.replace("ō", "ou").replace("Ō", "Ou")
                .replace("ū", "uu").replace("Ū", "Uu")
            listOf(title, romanized).flatMap { spelling ->
                val clean = Normalizer.normalize(spelling, Normalizer.Form.NFD)
                    .replace(DIACRITICS, "")
                    .replace(EDITORIAL_SUFFIX, " ")
                    .replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()
                val shortVowels = clean.replace(LONG_VOWELS, "$1")
                listOf(clean, shortVowels, shortVowels.replace(ROMAJI_OU, "o"),
                    shortVowels.replace(TERMINAL_U, "uu"))
            }
        }.filter { it.isNotBlank() }.distinct()

        fun normalize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(DIACRITICS, "").trim().lowercase(Locale.ROOT)
            .replace(EDITORIAL_SUFFIX, " ")
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ").trim()

        fun similarity(left: String, right: String): Double {
            if (left == right) return 1.0
            if (left.isEmpty() || right.isEmpty()) return 0.0
            val leftCore = left.replace(VOLUME_NUMBER, " ").replace(HARMLESS_NUMBER, " ")
                .replace(WHITESPACE, " ").trim()
            val rightCore = right.replace(VOLUME_NUMBER, " ").replace(HARMLESS_NUMBER, " ")
                .replace(WHITESPACE, " ").trim()
            if (SEQUEL_NUMBER.findAll(leftCore).map { it.value }.toList() !=
                SEQUEL_NUMBER.findAll(rightCore).map { it.value }.toList()) return 0.0
            val leftTokens = leftCore.split(' ').filter { it.isNotEmpty() }.toSet()
            val rightTokens = rightCore.split(' ').filter { it.isNotEmpty() }.toSet()
            if (rightTokens.isNotEmpty() && leftTokens.containsAll(rightTokens)) return 1.0
            val a = leftCore.replace(" ", "")
            val b = rightCore.replace(" ", "")
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var previous = IntArray(b.length + 1) { it }
            for (i in a.indices) {
                val row = IntArray(b.length + 1)
                row[0] = i + 1
                for (j in b.indices) {
                    row[j + 1] = minOf(row[j] + 1, previous[j + 1] + 1,
                        previous[j] + if (a[i] == b[j]) 0 else 1)
                }
                previous = row
            }
            val editSimilarity = 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
            val tokenSimilarity = leftTokens.intersect(rightTokens).size.toDouble() / maxOf(leftTokens.size, rightTokens.size)
            return maxOf(editSimilarity, tokenSimilarity)
        }

        private val EDITORIAL_SUFFIX = Regex("(?:\\s*(?:\\((?:official|color|webtoon|digital)\\)|\\bscanlation\\b)\\s*)+$", RegexOption.IGNORE_CASE)
        private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s]")
        private val WHITESPACE = Regex("\\s+")
        private val DIACRITICS = Regex("\\p{M}+")
        private val LONG_VOWELS = Regex("([aeiou])\\1+", RegexOption.IGNORE_CASE)
        private val ROMAJI_OU = Regex("ou", RegexOption.IGNORE_CASE)
        private val TERMINAL_U = Regex("(?<=[a-z])u\\b", RegexOption.IGNORE_CASE)
        private val VOLUME_NUMBER = Regex("\\b(?:vol(?:ume)?|chapter|ch)\\s+\\d+\\b")
        private val HARMLESS_NUMBER = Regex("\\b(?:0|\\d{3,})\\b")
        private val SEQUEL_NUMBER = Regex("\\b[1-9]\\d?\\b")
    }
}
