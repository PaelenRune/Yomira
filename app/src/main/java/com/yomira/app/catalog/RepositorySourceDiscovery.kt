package com.yomira.app.catalog

import com.yomira.domain.extension.*
import kotlinx.coroutines.CancellationException

/** Searches supported source catalogs advertised by the index; the index alone has no titles. */
class RepositorySourceDiscovery(private val bridge: SourceBridge) : SourceDiscovery {
    override suspend fun discover(series: SeriesIdentity, extensions: List<ExtensionDescriptor>): List<SourceCandidate> {
        val names = (listOf(series.title) + series.aliases).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return buildList {
            for (extension in extensions) {
                for (source in extension.sources.filter { it.name.equals("MangaDex", true) }) {
                    try {
                        val manga = names.firstNotNullOfOrNull { query ->
                            bridge.getMangaList(extension.packageName, source.id, query).firstOrNull { found ->
                                names.any { it.trim().equals(found.title.trim(), true) }
                            }
                        } ?: continue
                        val chapters = bridge.getChapterList(extension.packageName, source.id, manga)
                        if (chapters.isNotEmpty()) add(SourceCandidate(extension, source, manga, chapters.size, chapters.mapNotNull { it.uploadedAt }.maxOrNull()))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    }
                }
            }
        }
    }
}
