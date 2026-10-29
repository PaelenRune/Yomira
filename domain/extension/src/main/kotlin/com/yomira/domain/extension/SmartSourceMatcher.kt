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
        // One shared subject: every name of the series folded by the unified
        // normalization engine and scored by the shared TitleMatcher.
        val subject = TitleMatcher.Subject(listOf(series.title) + series.aliases)
        val observations = coroutineScope {
            val permits = Semaphore(8)
            eligible.flatMap { extension ->
                extension.sources.filter {
                    accepts(it, preferences) && (extension.packageName to it.id) !in excluded
                }.map { source ->
                    async { permits.withPermit { probe(extension, source, series, subject) } }
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
        val maxChapters = candidates.maxOfOrNull { it.chapterCount } ?: 1
        val now = currentTimeMillis()
        val ranked = candidates.distinctBy { it.extension.packageName to it.source.id }
            .sortedWith(compareByDescending<SourceCandidate> { score(it, preferences, maxChapters, now) }
                .thenByDescending { it.chapterCount }
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
        subject: TitleMatcher.Subject,
    ): Observation = try {
        withTimeout(15_000L) {
            val startedAt = nanoTime()
            val visited = mutableSetOf<String>()
            for (query in queries(series)) {
                val results = bridge.getMangaList(extension.packageName, source.id, query)
                    .filter { TitleMatcher.matches(it.title, subject) }
                    .sortedByDescending { title -> TitleMatcher.score(title.title, subject) }
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

    /** Bounded evidence weights; unknown dates/health never earn a fabricated bonus. */
    private fun score(candidate: SourceCandidate, preferences: ReaderPreferences, maxChapters: Int, now: Long): Double {
        val language = if (languageRank(candidate.source, preferences) == 2) 1000.0 else 0.0
        val completeness = 500.0 * candidate.chapterCount / maxChapters.coerceAtLeast(1)
        val recency = candidate.latestChapterAt?.let {
            val ageDays = (now.toDouble() - it.toDouble()).coerceAtLeast(0.0) / 86_400_000.0
            200.0 / (1.0 + ageDays / 30.0)
        } ?: 0.0
        val health = if (candidate.health == SourceHealth.HEALTHY) 300.0 else 0.0
        val speed = candidate.responseTimeMillis?.let { 100.0 / (1.0 + it.coerceAtLeast(0) / 1000.0) } ?: 0.0
        return language + completeness + recency + health + speed
    }

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
            ((listOf(series.title) + series.aliases))
                .flatMap(TitleNormalizer::searchVariants)
                .distinct()

        /** Shared by discovery adapters so they do not discard fuzzy matches before routing. */
        fun matchesTitle(title: String, aliases: Collection<String>): Boolean =
            TitleMatcher.matches(title, TitleMatcher.Subject(aliases))
    }
}
