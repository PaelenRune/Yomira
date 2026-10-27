package com.yomira.app.catalog

import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.entity.ChapterEntity
import com.yomira.domain.extension.SmartMatchResult
import com.yomira.domain.extension.SourceChapterLocation
import java.util.Date

/** Publish the selected index to the same Room flow observed by details and reader. */
class SourceMatchActivator(private val chapters: ChapterDao) {
    suspend fun activate(mangaId: String, result: SmartMatchResult.Ready) {
        if (!result.canRead) return
        val selected = result.selected
        val entities = result.chapters.distinctBy { it.id }.mapIndexed { index, chapter ->
            val location = SourceChapterLocation(selected.extension.packageName, selected.source.id, chapter.id).encode()
            ChapterEntity(
                id = "$mangaId:$location",
                mangaId = mangaId,
                chapterNumber = chapter.number?.takeIf { it.isFinite() } ?: (index + 1).toDouble(),
                title = chapter.name,
                releaseDate = chapter.uploadedAt?.let(::Date),
                scanlationGroup = selected.source.name,
                url = location,
            )
        }
        chapters.replaceForManga(mangaId, entities)
    }
}
