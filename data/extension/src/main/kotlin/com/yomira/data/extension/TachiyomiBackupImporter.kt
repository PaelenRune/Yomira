package com.yomira.data.extension

import com.yomira.domain.extension.BackupFormat
import com.yomira.domain.extension.BackupImportBatch
import com.yomira.domain.extension.BackupImportReport
import com.yomira.domain.extension.BackupImportTarget
import com.yomira.domain.extension.ImportedLibraryEntry
import com.yomira.domain.extension.ImportedReadingProgress
import com.yomira.domain.extension.ImportedSeries
import com.yomira.domain.extension.ImportedSeriesStatus

/**
 * Moves a Tachiyomi / Mihon backup into Yomira's Room database.
 *
 * Import policy (all deterministic, so re-running an import is safe):
 * - a Yomira catalogue id is minted with the usual `sourceId:remoteId` shape, using the
 *   backup's numeric Mihon source id and the source URL, which makes the import idempotent
 *   and lets the SmartSourceMatcher later re-link the row to a sandbox extension;
 * - only followed series move: `favorite == true` or at least one known category;
 *   everything else is counted, not silently dropped without a trace;
 * - duplicate `(source, url)` rows merge (metadata of the first occurrence wins, chapters
 *   are unioned by chapter URL);
 * - per-chapter `read` flags collapse into one [ImportedReadingProgress] row per series:
 *   the highest read chapter wins, ties break on the furthest page, and the timestamp is
 *   the newest `dateFetch` observed on a read chapter;
 * - category ids resolve through `categoriesMap` first, then to the backup's category names;
 *   the library row keeps the first resolved name (Yomira stores one category per title).
 *
 * The parsed document goes through [BackupImportBatch], so `:data:extension` stays a pure
 * JVM module; the Room implementation of [BackupImportTarget] lives in the app layer.
 */
class TachiyomiBackupImporter(private val parser: TachiyomiBackupParser = TachiyomiBackupParser()) {

    suspend fun import(bytes: ByteArray, target: BackupImportTarget): BackupImportReport =
        import(parser.parse(bytes), target)

    suspend fun import(document: TachiyomiBackupDocument, target: BackupImportTarget): BackupImportReport {
        val outcome = buildBatch(document)
        if (outcome.batch !== BackupImportBatch.EMPTY) target.import(outcome.batch)
        return BackupImportReport(
            format = outcome.format,
            seriesImported = outcome.batch.mangas.size,
            libraryEntries = outcome.batch.library.size,
            readingProgressRows = outcome.batch.readingProgress.size,
            skippedNotFollowed = outcome.skippedNotFollowed,
            skippedIncomplete = outcome.skippedIncomplete,
            duplicateSeries = outcome.duplicateSeries,
            orphanChapters = outcome.orphanChapters,
            chaptersObserved = outcome.chaptersObserved,
        )
    }

    /** Exposed for tests and callers that want the payload without persisting it. */
    fun buildBatch(document: TachiyomiBackupDocument): ImportOutcome {
        val categoryName = HashMap<Long, String>()
        document.categories.forEach { category ->
            val id = category.id
            if (id != null && category.name.isNotBlank()) categoryName.putIfAbsent(id, category.name)
        }

        // JSON-era foreign-key join: standalone chapters attach through `manga` -> rowId.
        val chaptersByRowId: MutableMap<Long, List<BackupChapterRecord>> = document.standaloneChapters
            .mapNotNull { chapter -> chapter.mangaRowId?.let { rowId -> rowId to chapter } }
            .groupBy({ it.first }, { it.second })
            .toMap(HashMap())
        var orphanChapters = document.standaloneChapters.count { it.mangaRowId == null }
        val chaptersObserved = document.mangas.sumOf { it.chapters.size } + document.standaloneChapters.size

        val seen = LinkedHashMap<String, MutableBackupSeries>()
        var skippedNotFollowed = 0
        var skippedIncomplete = 0
        var duplicateSeries = 0

        for (manga in document.mangas) {
            if (manga.url.isBlank() || manga.title.isBlank()) {
                skippedIncomplete++
                continue
            }
            val followed = manga.favorite || manga.categoryIds.isNotEmpty()
            if (!followed) {
                skippedNotFollowed++
                continue
            }
            val id = "${manga.sourceId}:${manga.url}"
            val existing = seen[id]
            if (existing != null) {
                duplicateSeries++
                existing.chapters += manga.chapters
                manga.rowId?.let { rowId -> chaptersByRowId[rowId]?.let { existing.chapters += it } }
                continue
            }
            val merged = MutableBackupSeries(manga)
            manga.rowId?.let { rowId ->
                val owned = chaptersByRowId.remove(rowId)
                if (owned != null) merged.chapters += owned
            }
            seen[id] = merged
        }
        orphanChapters += chaptersByRowId.values.sumOf { it.size }

        val mangas = ArrayList<ImportedSeries>(seen.size)
        val library = ArrayList<ImportedLibraryEntry>(seen.size)
        val progress = ArrayList<ImportedReadingProgress>(seen.size)
        for ((id, series) in seen) {
            val manga = series.manga
            mangas += ImportedSeries(
                id = id,
                sourceId = manga.sourceId.toString(),
                title = manga.title,
                coverUrl = manga.thumbnailUrl,
                author = manga.author,
                artist = manga.artist,
                synopsis = manga.description,
                status = mapStatus(manga.status),
                genres = manga.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            )
            library += ImportedLibraryEntry(
                mangaId = id,
                inLibrary = true,
                category = manga.categoryIds
                    .asSequence()
                    .mapNotNull { rawId -> categoryName[document.categoryAlias[rawId] ?: rawId] }
                    .firstOrNull(),
                addedAt = manga.dateAdded,
            )
            collapseProgress(id, series)?.let(progress::add)
        }
        val batch = if (mangas.isEmpty()) BackupImportBatch.EMPTY
        else BackupImportBatch(mangas.toList(), library.toList(), progress.toList())
        return ImportOutcome(
            format = document.format,
            batch = batch,
            skippedNotFollowed = skippedNotFollowed,
            skippedIncomplete = skippedIncomplete,
            duplicateSeries = duplicateSeries,
            orphanChapters = orphanChapters,
            chaptersObserved = chaptersObserved.coerceAtLeast(0),
        )
    }

    private fun collapseProgress(mangaId: String, series: MutableBackupSeries): ImportedReadingProgress? {
        val read = series.chapters
            .distinctBy { if (it.url.isNotBlank()) it.url else "n:${it.chapterNumber}:${it.name}" }
            .filter { it.read }
            .ifEmpty { return null }
        val furthest = read.reduce { best, candidate ->
            when {
                candidate.chapterNumber > best.chapterNumber -> candidate
                candidate.chapterNumber < best.chapterNumber -> best
                candidate.lastPageRead > best.lastPageRead -> candidate
                else -> best
            }
        }
        val lastReadAt = read.maxOf { maxOf(it.dateFetch, it.dateUpload) }.coerceAtLeast(0L)
        return ImportedReadingProgress(
            mangaId = mangaId,
            chapterNumber = furthest.chapterNumber,
            pageIndex = furthest.lastPageRead,
            lastReadAt = lastReadAt,
        )
    }

    private class MutableBackupSeries(val manga: BackupMangaRecord) {
        val chapters = manga.chapters.toMutableList()
    }

    data class ImportOutcome(
        val format: BackupFormat,
        val batch: BackupImportBatch,
        val skippedNotFollowed: Int,
        val skippedIncomplete: Int,
        val duplicateSeries: Int,
        val orphanChapters: Int,
        val chaptersObserved: Int,
    )

    companion object {
        /** Tachiyomi status ints: 0 unknown, 1 ongoing, 2 completed, 3 licensed,
         * 4 publishing finished, 5 cancelled, 6 on hiatus. */
        fun mapStatus(raw: Int): ImportedSeriesStatus = when (raw) {
            1 -> ImportedSeriesStatus.ONGOING
            2 -> ImportedSeriesStatus.COMPLETED
            3 -> ImportedSeriesStatus.ONGOING // Licensed titles are still being released.
            4 -> ImportedSeriesStatus.COMPLETED
            5 -> ImportedSeriesStatus.CANCELLED
            6 -> ImportedSeriesStatus.HIATUS
            else -> ImportedSeriesStatus.UNKNOWN
        }
    }
}
