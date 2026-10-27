package com.yomira.domain.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

class SmartSourceMatcher(
    private val extensions: ExtensionRepository,
    private val bridge: SourceBridge,
    @Suppress("UNUSED_PARAMETER") discovery: SourceDiscovery,
    @Suppress("UNUSED_PARAMETER") currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun match(series: SeriesIdentity, preferences: ReaderPreferences): SmartMatchResult =
        route(series, preferences, emptySet())

    /** Hosts can silently re-route after a reader failure; never retry a failed source in this race. */
    suspend fun failover(
        series: SeriesIdentity,
        preferences: ReaderPreferences,
        failedSources: Collection<SourceCandidate>,
    ): SmartMatchResult = route(series, preferences,
        failedSources.map { it.extension.packageName to it.source.id }.toSet())

    private suspend fun route(
        series: SeriesIdentity,
        preferences: ReaderPreferences,
        excluded: Set<Pair<String, String>>,
    ): SmartMatchResult {
        val failures = mutableListOf<SourceFailure>()
        val installed = extensions.installed().filter { it.trusted }.associateBy { it.packageName }
        // Legacy providers may still obtain descriptors from the user's repository snapshot.
        val legacy = if (installed.values.any { it.descriptor == null }) extensions.available() else emptyList()
        val eligible = installed.values.mapNotNull { entry ->
            entry.descriptor?.takeIf { it.packageName == entry.packageName }
                ?: legacy.firstOrNull { it.packageName == entry.packageName }
        }.filter {
            (preferences.allowAdultContent || !it.nsfw) && it.sources.any { source -> accepts(source, preferences) }
        }
        val candidates = mutableListOf<SourceCandidate>()
        val chaptersBySource = mutableMapOf<Pair<String, String>, List<SourceChapter>>()
        val names = queries(series).map(::normalize).toSet()
        val observations = coroutineScope {
            val permits = Semaphore(8)
            eligible.flatMap { extension ->
                extension.sources.filter {
                    accepts(it, preferences) && (extension.packageName to it.id) !in excluded
                }.map { source ->
                    async { permits.withPermit { probe(extension, source, series, names) } }
                }
            }.awaitAll()
        }
        for (observation in observations) {
            observation.failure?.let { failures += it }
            observation.candidate?.let {
                candidates += it
                chaptersBySource[it.extension.packageName to it.source.id] = observation.chapters
            }
        }
        val ranked = candidates.distinctBy { it.extension.packageName to it.source.id }
            .sortedWith(compareByDescending<SourceCandidate> { it.chapterCount }
                .thenByDescending { it.latestChapterAt ?: Long.MIN_VALUE })
        val best = ranked.firstOrNull()
            ?: return if (failures.isEmpty()) SmartMatchResult.NoMatch else SmartMatchResult.Unavailable(failures)
        return SmartMatchResult.Ready(best, chaptersBySource.getValue(best.extension.packageName to best.source.id), ranked.drop(1))
    }

    private data class Observation(
        val candidate: SourceCandidate? = null,
        val chapters: List<SourceChapter> = emptyList(),
        val failure: SourceFailure? = null,
    )

    private suspend fun probe(
        extension: ExtensionDescriptor,
        source: ExtensionSource,
        series: SeriesIdentity,
        names: Set<String>,
    ): Observation = try {
        withTimeout(15_000L) {
            val startedAt = nanoTime()
            val visited = mutableSetOf<String>()
            for (query in queries(series)) {
                val results = bridge.getMangaList(extension.packageName, source.id, query)
                    .filter { matches(it.title, names) }
                    .sortedByDescending { title -> names.maxOf { similarity(normalize(title.title), it) } }
                for (manga in results) {
                    if (!visited.add(manga.id)) continue
                    val chapters = bridge.getChapterList(extension.packageName, source.id, manga).distinctBy { it.id }
                    if (chapters.isEmpty()) {
                        continue
                    }
                    val elapsedMillis = ((nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0)
                    return@withTimeout Observation(SourceCandidate(extension, source, manga, chapters.size,
                        chapters.mapNotNull { it.uploadedAt }.maxOrNull(), SourceHealth.HEALTHY, elapsedMillis), chapters)
                }
            }
            Observation()
        }
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        Observation(failure = SourceFailure(source.id, "Source timed out"))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Observation(failure = SourceFailure(source.id, error.message ?: "Source unavailable"))
    }

    /** Language is an eligibility filter only, never a ranking factor. */
    private fun accepts(source: ExtensionSource, preferences: ReaderPreferences): Boolean =
        languageRank(source, preferences) > 0

    /**
     * 2 = chosen language, 1 = multilingual or English fallback,
     * 0 = unrelated language (filtered by [accepts]).
     */
    private fun languageRank(source: ExtensionSource, preferences: ReaderPreferences): Int {
        val sourceLanguage = source.language.trim().lowercase(java.util.Locale.ROOT).substringBefore('-').substringBefore('_')
        return when {
            sourceLanguage == "all" || sourceLanguage == "multi" || sourceLanguage == "mul" -> 1
            preferences.language.accepts(sourceLanguage) -> 2
            sourceLanguage == "en" -> 1
            else -> 0
        }
    }

    companion object {
        /** Use every supplied localized name; translation cannot be inferred from Romaji alone. */
        private fun queries(series: SeriesIdentity): List<String> =
            expandedQueries(listOf(series.title) + series.aliases)

        private fun expandedQueries(titles: Collection<String>): List<String> = titles.flatMap { title ->
            val romanized = title.replace("ō", "ou").replace("Ō", "Ou")
                .replace("ū", "uu").replace("Ū", "Uu")
            listOf(title, romanized).flatMap { spelling ->
                val clean = java.text.Normalizer.normalize(spelling, java.text.Normalizer.Form.NFD)
                    .replace(DIACRITICS, "")
                    .replace(EDITORIAL_SUFFIX, " ")
                    .replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()
                val shortVowels = clean.replace(LONG_VOWELS, "$1")
                listOf(clean, shortVowels, shortVowels.replace(ROMAJI_OU, "o"),
                    shortVowels.replace(TERMINAL_U, "uu"))
            }
        }.filter { it.isNotBlank() }.distinct()

        /** Shared by discovery adapters so they do not discard fuzzy matches before routing. */
        fun matchesTitle(title: String, aliases: Collection<String>): Boolean =
            matches(title, expandedQueries(aliases).map(::normalize).toSet())

        private fun normalize(title: String): String = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
            .replace(DIACRITICS, "").trim().lowercase(java.util.Locale.ROOT)
            .replace(EDITORIAL_SUFFIX, " ")
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ").trim()

        private fun matches(title: String, names: Set<String>): Boolean {
            val normalized = normalize(title)
            return normalized.isNotEmpty() && names.any { similarity(normalized, it) >= 0.75 }
        }

        private fun similarity(left: String, right: String): Double {
            if (left == right) return 1.0
            if (left.isEmpty() || right.isEmpty()) return 0.0
            // Volume metadata, years and intrinsic large numbers are not sequel identifiers.
            val leftCore = left.replace(VOLUME_NUMBER, " ").replace(HARMLESS_NUMBER, " ")
                .replace(WHITESPACE, " ").trim()
            val rightCore = right.replace(VOLUME_NUMBER, " ").replace(HARMLESS_NUMBER, " ")
                .replace(WHITESPACE, " ").trim()
            // Keep "A Series", "A Series 1" and "A Series 2" distinct, even under containment.
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
