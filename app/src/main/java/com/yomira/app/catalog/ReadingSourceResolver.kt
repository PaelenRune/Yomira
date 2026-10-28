package com.yomira.app.catalog

import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.model.Manga
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.SeriesIdentity
import com.yomira.domain.extension.SmartMatchResult
import com.yomira.domain.extension.SmartSourceMatcher

/** Shared reader-entry gate for detail, home resume and Updates Hub, including cached chapters. */
class ReadingSourceResolver(private val matcher: SmartSourceMatcher, private val chapters: ChapterDao) {
    suspend fun resolve(manga: Manga, requestedChapterId: String, preferences: ReaderPreferences,
        requestedNumber: Double? = null): String? {
        // Capture the logical number before replacing source-specific IDs. History is not rewritten.
        val requested = chapters.getById(requestedChapterId)?.takeIf { it.mangaId == manga.id }
        val result = matcher.match(SeriesIdentity(manga.id, manga.title, manga.altTitles.toSet()), preferences)
        if (result !is SmartMatchResult.Ready || !result.canRead) return null
        SourceMatchActivator(chapters).activate(manga.id, result)
        val selected = chapters.getByMangaId(manga.id)
        return selected.firstOrNull { it.id == requestedChapterId }?.id
            ?: (requested?.chapterNumber ?: requestedNumber)?.let { number ->
                selected.firstOrNull { it.chapterNumber == number }?.id
            }
    }
}
