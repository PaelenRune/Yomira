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
    private val discovery: SourceDiscovery,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
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
        val eligible = extensions.available().filter {
            (preferences.allowAdultContent || !it.nsfw) && it.sources.any { source -> accepts(source, preferences) }
        }
        val candidates = mutableListOf<SourceCandidate>()
        val chaptersBySource = mutableMapOf<Pair<String, String>, List<SourceChapter>>()
        val names = (series.aliases + series.title).map(::normalize).filter { it.isNotEmpty() }.toSet()
        val observations = coroutineScope {
            val permits = Semaphore(8)
            eligible.filter { it.packageName in installed }.flatMap { extension ->
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
        val missing = eligible.filter { it.packageName !in installed }
        if (missing.isNotEmpty()) {
            try {
                val allowed = missing.associateBy { it.packageName }
                candidates += discovery.discover(series, missing).filter {
                    allowed[it.extension.packageName] == it.extension && accepts(it.source, preferences) &&
                        it.extension.packageName to it.source.id !in excluded &&
                        it.health != SourceHealth.BLOCKED && matches(it.manga.title, names) &&
                        (languageRank(it.source, preferences) == 2 || it.chapterCount > 0)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += SourceFailure("catalog", error.message ?: "Catalog unavailable")
            }
        }
        val now = currentTimeMillis()
        val maxChapters = candidates.maxOfOrNull { it.chapterCount }?.coerceAtLeast(1) ?: 1
        val ranked = candidates.distinctBy { it.extension.packageName to it.source.id }
            .sortedWith(compareByDescending<SourceCandidate> { it.chapterCount > 0 }
                .thenByDescending { qualityScore(it, preferences, maxChapters, now) }
                .thenByDescending { it.latestChapterAt ?: Long.MIN_VALUE }
                .thenByDescending { it.extension.packageName in installed }
                .thenBy { it.extension.packageName }.thenBy { it.source.id })
        val best = ranked.firstOrNull()
            ?: return if (failures.isEmpty()) SmartMatchResult.NoMatch else SmartMatchResult.Unavailable(failures)
        return if (best.extension.packageName in installed) {
            SmartMatchResult.Ready(best, chaptersBySource.getValue(best.extension.packageName to best.source.id), ranked.drop(1))
        } else {
            SmartMatchResult.MissingExtension(best, best.chapterCount, best.extension.apkUrl, ranked.drop(1))
        }
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
            val manga = (listOf(series.title) + series.aliases).distinct().firstNotNullOfOrNull { query ->
                bridge.getMangaList(extension.packageName, source.id, query)
                    .filter { matches(it.title, names) }
                    .maxByOrNull { title -> names.maxOf { similarity(normalize(title.title), it) } }
            } ?: return@withTimeout Observation()
            val chapters = bridge.getChapterList(extension.packageName, source.id, manga).distinctBy { it.id }
            val elapsedMillis = ((nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0)
            Observation(SourceCandidate(extension, source, manga, chapters.size,
                chapters.mapNotNull { it.uploadedAt }.maxOrNull(), SourceHealth.HEALTHY, elapsedMillis), chapters)
        }
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        Observation(failure = SourceFailure(source.id, "Source timed out"))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Observation(failure = SourceFailure(source.id, error.message ?: "Source unavailable"))
    }

    /**
     * Language gate for source matching. The language picked during onboarding is
     * authoritative: sources in that language are eligible, multilingual ("all")
     * sources are eligible as a fallback, and single-language sources in any other
     * language are dropped. Region variants ("tr-TR") count as the base language.
     */
    private fun accepts(source: ExtensionSource, preferences: ReaderPreferences): Boolean =
        languageRank(source, preferences) > 0

    /**
     * Sort weight: 2 = exact match with the user's chosen language, 1 = multilingual
     * source, 0 = unrelated language (already filtered by [accepts]).
     */
    private fun languageRank(source: ExtensionSource, preferences: ReaderPreferences): Int {
        val sourceLanguage = source.language.trim().lowercase(java.util.Locale.ROOT).substringBefore('-').substringBefore('_')
        return when {
            sourceLanguage == "all" || sourceLanguage == "multi" || sourceLanguage == "mul" -> 1
            preferences.language.accepts(sourceLanguage) -> 2
            else -> 0
        }
    }

    /** Bounded runtime weights; neither package names nor favorite sites affect quality. */
    private fun qualityScore(candidate: SourceCandidate, preferences: ReaderPreferences, maxChapters: Int, now: Long): Double {
        val language = if (languageRank(candidate.source, preferences) == 2) 1000.0 else 0.0
        val completeness = 500.0 * candidate.chapterCount / maxChapters
        val recency = candidate.latestChapterAt?.takeIf { it > 0 }?.let { uploadedAt ->
            val ageDays = (now.coerceAtLeast(uploadedAt) - uploadedAt) / 86_400_000L
            200.0 * (30L - ageDays).coerceIn(0L, 30L) / 30.0
        } ?: 0.0
        val health = if (candidate.health == SourceHealth.HEALTHY) 300.0 else 0.0
        val speed = candidate.responseTimeMillis?.let { 100.0 * (10_000L - it).coerceIn(0L, 10_000L) / 10_000.0 } ?: 0.0
        return language + completeness + recency + health + speed
    }

    companion object {
        /** Shared by discovery adapters so they do not discard fuzzy matches before routing. */
        fun matchesTitle(title: String, aliases: Collection<String>): Boolean =
            matches(title, aliases.map(::normalize).filter { it.isNotEmpty() }.toSet())

        private fun normalize(title: String): String = title.trim().lowercase(java.util.Locale.ROOT)
            .replace(EDITORIAL_SUFFIX, " ")
            .replace(PUNCTUATION, " ")
            .replace(Regex("\\s+"), " ").trim()

        private fun matches(title: String, names: Set<String>): Boolean {
            val normalized = normalize(title)
            return normalized.isNotEmpty() && names.any { similarity(normalized, it) >= 0.85 }
        }

        private fun similarity(left: String, right: String): Double {
            if (left == right) return 1.0
            if (left.isEmpty() || right.isEmpty()) return 0.0
            // Edit distance alone would merge titles such as "A Series" and "A Series 2".
            if (NUMBERS.findAll(left).map { it.value }.toList() != NUMBERS.findAll(right).map { it.value }.toList()) return 0.0
            val a = left.replace(" ", "")
            val b = right.replace(" ", "")
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
            val leftTokens = left.split(' ').toSet()
            val rightTokens = right.split(' ').toSet()
            // Divide by the larger set: a short title must not absorb an unrelated sequel/spinoff.
            val tokenSimilarity = leftTokens.intersect(rightTokens).size.toDouble() / maxOf(leftTokens.size, rightTokens.size)
            return maxOf(editSimilarity, tokenSimilarity)
        }

        private val EDITORIAL_SUFFIX = Regex("(?:\\s*(?:\\((?:official|color|webtoon|digital)\\)|\\bscanlation\\b)\\s*)+$", RegexOption.IGNORE_CASE)
        private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s]")
        private val NUMBERS = Regex("\\d+")
    }
}
