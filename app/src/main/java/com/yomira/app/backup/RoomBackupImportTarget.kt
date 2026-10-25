package com.yomira.app.backup

import androidx.room.withTransaction
import com.yomira.core.database.YomiraDatabase
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.SyncDataDao
import com.yomira.core.database.dao.findByAnyTitle
import com.yomira.core.database.entity.LibraryEntryEntity
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.database.entity.ReadingProgressEntity
import com.yomira.core.model.ContentRating
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import com.yomira.domain.extension.BackupImportBatch
import com.yomira.domain.extension.BackupImportTarget
import com.yomira.domain.extension.ImportedReadingProgress
import com.yomira.domain.extension.ImportedSeries
import com.yomira.domain.extension.ImportedSeriesStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists Tachiyomi / Mihon imports into Yomira's Room database — the bridge between the
 * pure-JVM importer in `:data:extension` and the catalogue.
 *
 * Merge rules (idempotent, so pressing "import" twice can never double a row):
 * - **Manga**: never overwrite an existing catalogue row. The backup row is either linked to
 *   the id it already has, merged into a seeded row with the same/alt title (same
 *   merge-before-insert rule the catalog merger uses), or inserted as a new `MangaEntity`.
 * - **Library**: union membership. `inLibrary` sticks to true, a locally newer row keeps its
 *   category, an untouched row adopts the backup's category.
 * - **Reading progress**: max-progress wins (furthest chapter, then furthest page), matching
 *   the cloud sync merger, so an old backup can never roll progress backwards.
 *
 * Everything runs inside one database transaction: a crash mid-import rolls back entirely.
 */
@Singleton
class RoomBackupImportTarget @Inject constructor(
    private val database: YomiraDatabase,
    private val mangaDao: MangaDao,
    private val syncDataDao: SyncDataDao,
) : BackupImportTarget {

    override suspend fun import(batch: BackupImportBatch) {
        if (batch === BackupImportBatch.EMPTY) return
        database.withTransaction {
            val resolvedIds = HashMap<String, String>(batch.mangas.size)
            for (series in batch.mangas) {
                resolvedIds[series.id] = resolveMangaRow(series)
            }

            val existingLibrary = syncDataDao.getAllLibraryEntries().associateBy { it.mangaId }
            val library = batch.library.mapNotNull { entry ->
                val mangaId = resolvedIds[entry.mangaId] ?: return@mapNotNull null
                val existing = existingLibrary[mangaId]
                if (existing == null) {
                    LibraryEntryEntity(
                        mangaId = mangaId,
                        inLibrary = entry.inLibrary,
                        category = entry.category,
                        updatedAt = entry.addedAt,
                    )
                } else {
                    val importWins = entry.addedAt >= existing.updatedAt
                    LibraryEntryEntity(
                        mangaId = mangaId,
                        inLibrary = existing.inLibrary || entry.inLibrary,
                        category = if (importWins) entry.category ?: existing.category else existing.category,
                        updatedAt = maxOf(existing.updatedAt, entry.addedAt),
                    )
                }
            }
            if (library.isNotEmpty()) syncDataDao.upsertLibraryEntries(library)

            val existingProgress = syncDataDao.getAllReadingProgress().associateBy { it.mangaId }
            val progress = batch.readingProgress.mapNotNull { entry ->
                val mangaId = resolvedIds[entry.mangaId] ?: return@mapNotNull null
                val existing = existingProgress[mangaId]
                val winner = mergeProgress(existing, entry, mangaId)
                if (winner == existing) null else winner
            }
            if (progress.isNotEmpty()) syncDataDao.upsertReadingProgress(progress)
        }
    }

    private suspend fun resolveMangaRow(series: ImportedSeries): String {
        if (mangaDao.getById(series.id) != null) return series.id
        // Same merge-before-insert rule as the catalog merger: a seeded row for the very same
        // title adopts the backup's library state instead of becoming a second card.
        mangaDao.findByAnyTitle(listOf(series.title))?.let { existing -> return existing.id }
        mangaDao.upsert(
            MangaEntity(
                id = series.id,
                title = series.title,
                coverUrl = series.coverUrl,
                author = series.author,
                artist = series.artist,
                synopsis = series.synopsis,
                status = series.status.toMangaStatus(),
                genres = series.genres,
                contentRating = if (series.adult) ContentRating.EXPLICIT else ContentRating.SAFE,
                sourceId = series.sourceId,
                readingMode = ReadingMode.MANGA_RTL,
            ),
        )
        return series.id
    }

    private fun mergeProgress(
        existing: ReadingProgressEntity?,
        imported: ImportedReadingProgress,
        mangaId: String,
    ): ReadingProgressEntity {
        val incoming = ReadingProgressEntity(
            mangaId = mangaId,
            chapterNumber = imported.chapterNumber,
            pageIndex = imported.pageIndex,
            lastReadAt = imported.lastReadAt,
        )
        if (existing == null) return incoming
        val importedWins = incoming.chapterNumber > existing.chapterNumber ||
            (incoming.chapterNumber == existing.chapterNumber && incoming.pageIndex > existing.pageIndex)
        return if (importedWins) incoming else existing.copy(lastReadAt = maxOf(existing.lastReadAt, incoming.lastReadAt))
    }

    private fun ImportedSeriesStatus.toMangaStatus(): MangaStatus = when (this) {
        ImportedSeriesStatus.ONGOING -> MangaStatus.ONGOING
        ImportedSeriesStatus.COMPLETED -> MangaStatus.COMPLETED
        ImportedSeriesStatus.HIATUS -> MangaStatus.HIATUS
        ImportedSeriesStatus.CANCELLED -> MangaStatus.CANCELLED
        ImportedSeriesStatus.UNKNOWN -> MangaStatus.UNKNOWN
    }
}
