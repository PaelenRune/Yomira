package com.yomira.domain.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SmartSourceMatcher(
    private val extensions: ExtensionRepository,
    private val bridge: SourceBridge,
    private val discovery: SourceDiscovery,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun match(series: SeriesIdentity, preferences: ReaderPreferences): SmartMatchResult {
        val failures = mutableListOf<SourceFailure>()
        val installed = extensions.installed().filter { it.trusted }.associateBy { it.packageName }
        val eligible = extensions.available().filter {
            (preferences.allowAdultContent || !it.nsfw) && it.sources.any { source -> accepts(source, preferences) }
        }
        val candidates = mutableListOf<SourceCandidate>()
        val chaptersBySource = mutableMapOf<Pair<String, String>, List<SourceChapter>>()
        val responseBonuses = mutableMapOf<Pair<String, String>, Long>()
        val names = (series.aliases + series.title).map(::normalize).filter { it.isNotEmpty() }.toSet()
        for (extension in eligible.filter { it.packageName in installed }) {
            for (source in extension.sources.filter { accepts(it, preferences) }) {
                try {
                    val startedAt = nanoTime()
                    val manga = (listOf(series.title) + series.aliases).distinct().firstNotNullOfOrNull { query ->
                        bridge.getMangaList(extension.packageName, source.id, query)
                            .firstOrNull { normalize(it.title) in names }
                    } ?: continue
                    val chapters = bridge.getChapterList(extension.packageName, source.id, manga).distinctBy { it.id }
                    candidates += SourceCandidate(extension, source, manga, chapters.size, chapters.mapNotNull { it.uploadedAt }.maxOrNull())
                    chaptersBySource[extension.packageName to source.id] = chapters
                    val elapsedMillis = ((nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0)
                    responseBonuses[extension.packageName to source.id] = 20L + (20L - elapsedMillis / 250L).coerceIn(0L, 20L)
                } catch (error: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    failures += SourceFailure(source.id, "Source timed out")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += SourceFailure(source.id, error.message ?: "Source unavailable")
                }
            }
        }
        val missing = eligible.filter { it.packageName !in installed }
        if (missing.isNotEmpty()) {
            try {
                val allowed = missing.associateBy { it.packageName }
                candidates += discovery.discover(series, missing).filter {
                    allowed[it.extension.packageName] == it.extension && accepts(it.source, preferences) &&
                        normalize(it.manga.title) in names &&
                        (languageRank(it.source, preferences) == 2 || it.chapterCount > 0)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += SourceFailure("catalog", error.message ?: "Catalog unavailable")
            }
        }
        val now = currentTimeMillis()
        val ranked = candidates.distinctBy { it.extension.packageName to it.source.id }
            .sortedWith(compareByDescending<SourceCandidate> { it.chapterCount > 0 }
                .thenByDescending { languageRank(it.source, preferences) }
                .thenByDescending { candidate ->
                    val key = candidate.extension.packageName to candidate.source.id
                    qualityScore(candidate, preferences, candidate.extension.packageName in installed, now) +
                        (responseBonuses[key] ?: 0L)
                }
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

    /** Language tiers are authoritative; quality only breaks ties inside a tier. */
    private fun qualityScore(candidate: SourceCandidate, preferences: ReaderPreferences, installed: Boolean, now: Long): Long {
        val language = if (languageRank(candidate.source, preferences) == 2) 1000L else 300L
        // A bounded recency bonus cannot overwhelm completeness. Timestamps are epoch millis.
        val recency = candidate.latestChapterAt?.takeIf { it > 0 }?.let { uploadedAt ->
            val ageDays = (now.coerceAtLeast(uploadedAt) - uploadedAt) / 86_400_000L
            (60L - ageDays).coerceIn(0L, 60L)
        } ?: 0L
        return language + candidate.chapterCount.toLong() * 2L + recency + if (installed) 100L else 0L
    }

    private fun normalize(title: String): String = title.trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
