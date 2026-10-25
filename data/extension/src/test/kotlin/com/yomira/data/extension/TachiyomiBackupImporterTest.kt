package com.yomira.data.extension

import com.yomira.domain.extension.BackupFormat
import com.yomira.domain.extension.BackupImportBatch
import com.yomira.domain.extension.BackupImportTarget
import com.yomira.domain.extension.ImportedSeriesStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TachiyomiBackupImporterTest {
    private val importer = TachiyomiBackupImporter()

    private class RecordingTarget : BackupImportTarget {
        val batches = mutableListOf<BackupImportBatch>()
        override suspend fun import(batch: BackupImportBatch) {
            batches += batch
        }
    }

    private fun chapter(
        url: String,
        number: Double,
        read: Boolean,
        lastPage: Int = 0,
        dateFetch: Long = 0L,
        rowId: Long? = null,
    ) = BackupChapterRecord(rowId, url, "Ch $number", null, read, false, lastPage, number, dateFetch, 0L)

    private fun manga(
        rowId: Long?,
        source: Long,
        url: String,
        title: String,
        favorite: Boolean = true,
        categoryIds: List<Long> = emptyList(),
        status: Int = 0,
        chapters: List<BackupChapterRecord> = emptyList(),
    ) = BackupMangaRecord(
        rowId = rowId,
        sourceId = source,
        url = url,
        title = title,
        artist = null,
        author = "Author",
        description = "Sınav sonrası bir dünya.",
        genres = listOf("Dram", " drama ", "Sein(en)"),
        thumbnailUrl = "https://covers.example/$url.jpg",
        status = status,
        favorite = favorite,
        dateAdded = 1_600_000_000_000L,
        categoryIds = categoryIds,
        chapters = chapters,
    )

    private fun document() = TachiyomiBackupDocument(
        format = BackupFormat.PROTOBUF,
        categories = listOf(
            BackupCategoryRecord(3L, "Okunuyor", 0L),
            BackupCategoryRecord(9L, "Arşiv", 1L),
        ),
        mangas = listOf(
            // Fully followed series: progress must collapse to the furthest READ chapter.
            manga(
                rowId = 10L, source = 5_601_175_488_615_850_849L, url = "series/furthest", title = "Uzak",
                categoryIds = listOf(3L), status = 1,
                chapters = listOf(
                    chapter("c1", 1.0, read = true, lastPage = 2, dateFetch = 1_600_100_000_000L),
                    chapter("c2", 12.5, read = true, lastPage = 4, dateFetch = 1_600_500_000_000L),
                    chapter("c3", 13.0, read = false), // unread chapters never advance progress
                ),
            ),
            // Same (source, url) again: must merge, and its read chapter is the true furthest.
            manga(
                rowId = null, source = 5_601_175_488_615_850_849L, url = "series/furthest", title = "Uzak",
                categoryIds = listOf(9L),
                chapters = listOf(chapter("c4", 20.0, read = true, lastPage = 1, dateFetch = 1_600_900_000_000L)),
            ),
            // Not followed anywhere: skipped but reported.
            manga(rowId = 11L, source = 2L, url = "series/ghost", title = "Hayalet", favorite = false),
            // Title-less junk row: skipped as incomplete.
            manga(rowId = 12L, source = 3L, url = "series/junk", title = "  "),
            // Category id that only resolves through the backup's categoriesMap.
            manga(rowId = 13L, source = 4L, url = "series/mapped", title = "Eşlenen", categoryIds = listOf(7L)),
        ),
        standaloneChapters = listOf(
            chapter("orphan", 5.0, read = true, rowId = 999L),
            chapter("owned", 2.5, read = true, lastPage = 6, dateFetch = 1_600_200_000_000L, rowId = 13L),
        ),
        categoryAlias = mapOf(7L to 9L),
    )

    @Test fun `import maps library, categories and collapsed progress`() = runTest {
        val target = RecordingTarget()
        val report = importer.import(document(), target)

        assertEquals(2, report.seriesImported) // ghost (not followed) + junk (blank title) skipped
        assertEquals(2, report.libraryEntries)
        assertEquals(2, report.readingProgressRows)
        assertEquals(1, report.skippedNotFollowed)
        assertEquals(1, report.skippedIncomplete)
        assertEquals(1, report.duplicateSeries)
        assertEquals(1, report.orphanChapters)
        assertEquals(6, report.chaptersObserved) // 3 + 1 inline, plus 2 standalone
        assertEquals(BackupFormat.PROTOBUF, report.format)

        val batch = target.batches.single()
        assertEquals(
            listOf("5601175488615850849:series/furthest", "4:series/mapped"),
            batch.mangas.map { it.id }.filter { it.startsWith("5") || it.startsWith("4") },
        )

        val furthest = batch.mangas.first { it.id == "5601175488615850849:series/furthest" }
        assertEquals("Uzak", furthest.title)
        assertEquals("5601175488615850849", furthest.sourceId)
        assertEquals(ImportedSeriesStatus.ONGOING, furthest.status)
        assertEquals(listOf("Dram", "drama", "Sein(en)"), furthest.genres)
        assertEquals("https://covers.example/series/furthest.jpg", furthest.coverUrl)

        // The furthest READ chapter across BOTH duplicate rows is c4 (20.0, page 1).
        val progress = batch.readingProgress.single { it.mangaId == furthest.id }
        assertEquals(20.0, progress.chapterNumber, 0.0)
        assertEquals(1, progress.pageIndex)
        assertEquals(1_600_900_000_000L, progress.lastReadAt)

        // Mapped category resolves 7 -> 9 -> "Arşiv"; direct id resolves 3 -> "Okunuyor".
        assertEquals("Arşiv", batch.library.first { it.mangaId == "4:series/mapped" }.category)
        assertEquals(
            batch.library.first { it.mangaId == furthest.id }.category,
            "Okunuyor",
        )
        assertEquals(1_600_000_000_000L, batch.library.first { it.mangaId == furthest.id }.addedAt)

        // The mapped series adopts its standalone chapter through the `manga` foreign key.
        val mappedProgress = batch.readingProgress.single { it.mangaId == "4:series/mapped" }
        assertEquals(2.5, mappedProgress.chapterNumber, 0.0)
        assertEquals(6, mappedProgress.pageIndex)
    }

    @Test fun `importing the same document twice yields identical batches`() = runTest {
        val target = RecordingTarget()
        importer.import(document(), target)
        importer.import(document(), target)
        assertEquals(target.batches.first(), target.batches.last())
    }

    @Test fun `nothing to import produces an empty report without touching the target`() = runTest {
        val target = RecordingTarget()
        val report = importer.import(TachiyomiBackupDocument(BackupFormat.JSON), target)
        assertEquals(0, report.seriesImported)
        assertTrue(target.batches.isEmpty())
        assertSame(BackupImportBatch.EMPTY, importer.buildBatch(TachiyomiBackupDocument(BackupFormat.JSON)).batch)
    }

    @Test fun `end to end import from gzipped protobuf bytes`() = runTest {
        val manga = ProtoTestWriter()
            .varintField(1, 8L)
            .stringField(2, "series/e2e")
            .stringField(3, "Uçtan Uca")
            .boolField(100, true)
            .messageField(
                16,
                ProtoTestWriter()
                    .stringField(1, "c1")
                    .stringField(2, "1")
                    .boolField(4, true)
                    .varintField(6, 9L)
                    .varintField(7, 42L)
                    .floatField(9, 3.5f),
            )
        val bytes = BackupTestFixtures.gzip(ProtoTestWriter().messageField(1, manga).toByteArray())
        val target = RecordingTarget()
        val report = importer.import(bytes, target)

        assertEquals(BackupFormat.PROTOBUF, report.format)
        assertEquals(1, report.seriesImported)
        val batch = target.batches.single()
        assertEquals("8:series/e2e", batch.mangas.single().id)
        val progress = batch.readingProgress.single()
        assertEquals(3.5, progress.chapterNumber, 0.0)
        assertEquals(9, progress.pageIndex)
        assertEquals(42L, progress.lastReadAt)
    }

    @Test fun `library-only entries (no read chapters) carry no progress row`() = runTest {
        val document = TachiyomiBackupDocument(
            format = BackupFormat.JSON,
            mangas = listOf(manga(rowId = null, source = 1L, url = "u", title = "Sadece Kitaplık")),
        )
        val report = importer.import(document, RecordingTarget())
        assertEquals(1, report.seriesImported)
        assertEquals(0, report.readingProgressRows)
    }

    @Test fun `status mapping covers every upstream code`() {
        assertEquals(ImportedSeriesStatus.UNKNOWN, TachiyomiBackupImporter.mapStatus(0))
        assertEquals(ImportedSeriesStatus.ONGOING, TachiyomiBackupImporter.mapStatus(1))
        assertEquals(ImportedSeriesStatus.COMPLETED, TachiyomiBackupImporter.mapStatus(2))
        assertEquals(ImportedSeriesStatus.ONGOING, TachiyomiBackupImporter.mapStatus(3))
        assertEquals(ImportedSeriesStatus.COMPLETED, TachiyomiBackupImporter.mapStatus(4))
        assertEquals(ImportedSeriesStatus.CANCELLED, TachiyomiBackupImporter.mapStatus(5))
        assertEquals(ImportedSeriesStatus.HIATUS, TachiyomiBackupImporter.mapStatus(6))
        assertEquals(ImportedSeriesStatus.UNKNOWN, TachiyomiBackupImporter.mapStatus(99))
    }
}
