package com.yomira.app.catalog

import com.yomira.domain.extension.*
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

/** Probe advertised sources without a site allowlist; unsupported/blocked runtimes simply fail over. */
class RepositorySourceDiscovery(private val bridge: SourceBridge) : SourceDiscovery {
    override suspend fun discover(series: SeriesIdentity, extensions: List<ExtensionDescriptor>): List<SourceCandidate> = coroutineScope {
        val names = (listOf(series.title) + series.aliases).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val permits = Semaphore(8)
        extensions.flatMap { extension ->
            extension.sources.map { source ->
                async {
                    permits.withPermit {
                        try {
                            withTimeout(15_000L) {
                                val started = System.nanoTime()
                                val manga = names.firstNotNullOfOrNull { query ->
                                    bridge.getMangaList(extension.packageName, source.id, query)
                                        .firstOrNull { SmartSourceMatcher.matchesTitle(it.title, names) }
                                } ?: return@withTimeout null
                                val chapters = bridge.getChapterList(extension.packageName, source.id, manga).distinctBy { it.id }
                                if (chapters.isEmpty()) null else SourceCandidate(extension, source, manga, chapters.size,
                                    chapters.mapNotNull { it.uploadedAt }.maxOrNull(), SourceHealth.HEALTHY,
                                    ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0))
                            }
                        } catch (_: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }
}
