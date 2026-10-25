package com.yomira.domain.extension

/**
 * Normalised, Yomira-shaped records produced by the Tachiyomi/Mihon backup importer and
 * consumed by a device storage implementation (the Room adapter lives in the app layer).
 *
 * The importer never talks to Room directly: `:data:extension` is a pure JVM module, so the
 * conversion boundary is expressed here as plain value types plus one port. Implementations
 * must apply a whole [BackupImportBatch] atomically — a half-imported library is worse than
 * a failed one, because the user would retry and double every merge rule.
 */
data class BackupImportBatch(
    val mangas: List<ImportedSeries>,
    val library: List<ImportedLibraryEntry>,
    val readingProgress: List<ImportedReadingProgress>,
) {
    init {
        require(mangas.map { it.id }.distinct().size == mangas.size) { "Duplicate manga IDs in batch" }
        require(library.map { it.mangaId }.distinct().size == library.size) { "Duplicate library entries in batch" }
        require(readingProgress.map { it.mangaId }.distinct().size == readingProgress.size) {
            "Duplicate reading progress in batch"
        }
        require(library.all { entry -> entry.mangaId in mangas.map { it.id } }) {
            "Library entry references unknown manga"
        }
        require(readingProgress.all { entry -> entry.mangaId in mangas.map { it.id } }) {
            "Reading progress references unknown manga"
        }
    }

    companion object {
        val EMPTY = BackupImportBatch(emptyList(), emptyList(), emptyList())
    }
}

/** Catalogue projection of one followed series from a Tachiyomi/Mihon backup. */
data class ImportedSeries(
    /** `sourceId:remoteUrl` — the same id shape Yomira mints for catalogue rows. */
    val id: String,
    /** Mihon's numeric source id as a string; it equals the Keiyoushi index source id. */
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val author: String?,
    val artist: String?,
    val synopsis: String?,
    /** Stable label so storage can map to its own status enum without reparsing. */
    val status: ImportedSeriesStatus,
    val genres: List<String>,
    /** True when the series is adult material (NSFW backup categories are rare; default false). */
    val adult: Boolean = false,
) {
    init {
        require(id.isNotBlank() && sourceId.isNotBlank() && title.isNotBlank()) {
            "Imported series needs id, sourceId and title"
        }
        require(genres.map { it.trim() }.filter { it.isNotEmpty() }.size == genres.size) {
            "Genres must be trimmed and non-blank"
        }
    }
}

enum class ImportedSeriesStatus { UNKNOWN, ONGOING, COMPLETED, HIATUS, CANCELLED }

/** Library membership carrying the backup's favourite flag, category and added timestamp. */
data class ImportedLibraryEntry(
    val mangaId: String,
    val inLibrary: Boolean,
    /** First known category name; `null` when the backup stored no category for the entry. */
    val category: String?,
    /** UTC epoch milliseconds the series was added to the old app's library. */
    val addedAt: Long,
) {
    init {
        require(mangaId.isNotBlank()) { "mangaId must not be blank" }
        require(category == null || category.isNotBlank()) { "category must be null or non-blank" }
        require(addedAt >= 0L) { "addedAt must not be negative" }
    }
}

/** Furthest read position reconstructed from the backup's per-chapter flags. */
data class ImportedReadingProgress(
    val mangaId: String,
    val chapterNumber: Double,
    /** Last page index reached inside [chapterNumber]; never negative. */
    val pageIndex: Int,
    /** UTC epoch milliseconds; 0 when the backup carried no timestamp at all. */
    val lastReadAt: Long,
) {
    init {
        require(mangaId.isNotBlank()) { "mangaId must not be blank" }
        require(chapterNumber.isFinite() && chapterNumber >= 0.0) { "chapterNumber must be finite and non-negative" }
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        require(lastReadAt >= 0L) { "lastReadAt must not be negative" }
    }
}

/** Device storage boundary for backup imports; implementations persist a batch transactionally. */
interface BackupImportTarget {
    suspend fun import(batch: BackupImportBatch)
}

/** Which physical format the imported file used. Reported for UX and telemetry. */
enum class BackupFormat { JSON, PROTOBUF }

/** Outcome counters of one import so the UI can state exactly what happened. */
data class BackupImportReport(
    val format: BackupFormat,
    val seriesImported: Int,
    val libraryEntries: Int,
    val readingProgressRows: Int,
    /** Series the backup listed but that were not in a library (no favourite flag, no category). */
    val skippedNotFollowed: Int = 0,
    /** Series without a usable title/url pair. */
    val skippedIncomplete: Int = 0,
    /** Repeated `(source, url)` pairs merged into a single row. */
    val duplicateSeries: Int = 0,
    /** Chapters whose `manga` foreign key matched no imported series. */
    val orphanChapters: Int = 0,
    /** Total chapters seen, read or not. */
    val chaptersObserved: Int = 0,
)
