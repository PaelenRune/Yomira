package com.yomira.domain.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SmartSourceMatcher(
    private val extensions: ExtensionRepository,
    private val bridge: SourceBridge,
    private val discovery: SourceDiscovery,
) {
    suspend fun match(series: SeriesIdentity, preferences: ReaderPreferences): SmartMatchResult {
        val failures = mutableListOf<SourceFailure>()
        val installed = extensions.installed().filter { it.trusted }.associateBy { it.packageName }
        val eligible = extensions.available().filter { preferences.allowAdultContent || !it.nsfw }
        val candidates = mutableListOf<SourceCandidate>()
        val chaptersBySource = mutableMapOf<Pair<String, String>, List<SourceChapter>>()
        for (extension in eligible.filter { it.packageName in installed }) {
            for (source in extension.sources.filter { accepts(it, preferences) }) {
                try {
                    val matches = bridge.getMangaList(extension.packageName, source.id, series.title)
                    val names = (series.aliases + series.title).map(::normalize).toSet()
                    val manga = matches.firstOrNull { normalize(it.title) in names } ?: continue
                    val chapters = bridge.getChapterList(extension.packageName, source.id, manga).distinctBy { it.id }
                    candidates += SourceCandidate(extension, source, manga, chapters.size, chapters.mapNotNull { it.uploadedAt }.maxOrNull())
                    chaptersBySource[extension.packageName to source.id] = chapters
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
                    allowed[it.extension.packageName] == it.extension && accepts(it.source, preferences)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += SourceFailure("catalog", error.message ?: "Catalog unavailable")
            }
        }
        val ranked = candidates.distinctBy { it.extension.packageName to it.source.id }
            .sortedWith(compareByDescending<SourceCandidate> { languageRank(it.source, preferences) }
                .thenByDescending { it.chapterCount }
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

    private fun normalize(title: String): String = title.trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
