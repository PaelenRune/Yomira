package com.yomira.data.extension

import com.yomira.domain.extension.BackupFormat

/**
 * Format-neutral projection of a Tachiyomi / Mihon backup, produced by [TachiyomiBackupParser]
 * before any Yomira policy (dedupe, follow-filter, progress collapse) is applied.
 *
 * Covered physical formats:
 * - **Protobuf** (`.tachibk` / `.proto.gz` of Mihon, Tachiyomi 1.x, SY 1.x, Aniyomi, Komikku):
 *   optional gzip, `Backup.backupManga = 1`, `Backup.backupCategories = 2`, chapters nested
 *   inside each manga (field 16), category ids as (packed) repeated varints (field 17).
 * - **JSON** (Tachiyomi 0.15.x era exports, TachiyomiSY `.tachibk` JSON, J2K exports and any
 *   gzipped variant): top-level `mangas` / `categories` / `chapters` / `categoriesMap`,
 *   chapters attached either inline or through the numeric `manga` foreign key.
 *
 * Key-name aliases (`thumbnail_url` vs `thumbnailUrl`, `chapter_number` vs `chapterNumber`,
 * `last_page_read` vs `lastPageRead` …) are accepted on the JSON path because every fork
 * wrote a slightly different spelling of the same data.
 */
data class TachiyomiBackupDocument(
    val format: BackupFormat,
    val categories: List<BackupCategoryRecord> = emptyList(),
    val mangas: List<BackupMangaRecord> = emptyList(),
    /** Chapters listed at the top level (JSON era) that still need a manga join. */
    val standaloneChapters: List<BackupChapterRecord> = emptyList(),
    /** `categoriesMap` of JSON-era backups: backup-local id -> id the manga list references. */
    val categoryAlias: Map<Long, Long> = emptyMap(),
)

/** One category with its backup-local id; `null` id when the writer omitted ids. */
data class BackupCategoryRecord(val id: Long?, val name: String, val order: Long)

data class BackupMangaRecord(
    /** Row id (`id`) that JSON-era chapter foreign keys reference; null when absent. */
    val rowId: Long?,
    /** Mihon's numeric source id (identical to the Keiyoushi index source id). */
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val thumbnailUrl: String?,
    /** Raw Tachiyomi status int (0 unknown … 6 on hiatus). */
    val status: Int,
    val favorite: Boolean,
    /** UTC epoch millis when the series entered the old library; 0 when unknown. */
    val dateAdded: Long,
    val categoryIds: List<Long>,
    val chapters: List<BackupChapterRecord>,
)

data class BackupChapterRecord(
    /** JSON-era foreign key to [BackupMangaRecord.rowId]; null for inline/proto chapters. */
    val mangaRowId: Long?,
    val url: String,
    val name: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Int,
    val chapterNumber: Double,
    /** Millis of the last chapter fetch — used as the reading-progress timestamp. */
    val dateFetch: Long,
    val dateUpload: Long,
)
