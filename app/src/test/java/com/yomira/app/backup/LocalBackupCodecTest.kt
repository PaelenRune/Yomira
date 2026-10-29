package com.yomira.app.backup

import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.model.ContentRating
import com.yomira.data.extension.TachiyomiBackupImporter
import com.yomira.data.extension.TachiyomiBackupParser
import com.yomira.data.sync.LibrarySyncEntry
import com.yomira.data.sync.ReadingHistorySyncEntry
import com.yomira.data.sync.ReadingProgressSyncEntry
import com.yomira.data.sync.SyncPayload
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class LocalBackupCodecTest {
    private fun backup(rating: ContentRating = ContentRating.MATURE): LocalBackup {
        val row = MangaEntity("anilist:12", "日本語 · 한글", sourceId = "anilist", contentRating = rating,
            altTitles = listOf("English"), coverUrl = "https://image.test/cover.jpg")
        return LocalBackup(metadata = listOf(BackupMetadata.from(row)), state = SyncPayload(
            deviceId = "local", library = listOf(LibrarySyncEntry(row.id, favorite = true,
                categories = listOf("Reading"), customTags = listOf("tag"))),
            readingProgress = listOf(ReadingProgressSyncEntry(row.id, 3.0, 9, 123)),
            readingHistory = listOf(ReadingHistorySyncEntry(row.id, "chapter:3", 3, 9, 10, 123, true)),
        ))
    }

    @Test fun filenameUsesYomiraAndMinutePrecision() {
        assertEquals("yomira-backup-20261029-0905.tachibk",
            LocalBackupCodec.filename(LocalDateTime.of(2026, 10, 29, 9, 5)))
    }
    @Test fun roundTripPreservesIdentityTierLibraryAndHistory() {
        for (rating in ContentRating.entries) {
            val original = backup(rating)
            val bytes = LocalBackupCodec.encode(original)
            assertEquals(0x1f, bytes[0].toInt() and 0xff)
            val restored = LocalBackupCodec.decodeNative(bytes)!!
            assertEquals(original, restored)
            assertEquals(original.metadata.single().toEntity(), restored.metadata.single().toEntity())
            assertEquals(rating, restored.metadata.single().toEntity().contentRating)
        }
    }
    @Test fun existingParserAcceptsGeneratedBackup() {
        val document = TachiyomiBackupParser().parse(LocalBackupCodec.encode(backup()))
        assertEquals("日本語 · 한글", document.mangas.single().title)
        assertEquals(1, TachiyomiBackupImporter().buildBatch(document).batch.library.size)
    }
    @Test fun foreignJsonRemainsOnExistingImporterPath() {
        val bytes = """{"mangas":[{"source":1,"url":"/series","title":"Series","favorite":true}]}""".toByteArray()
        assertNull(LocalBackupCodec.decodeNative(bytes))
        assertEquals(1, TachiyomiBackupImporter().buildBatch(TachiyomiBackupParser().parse(bytes)).batch.library.size)
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedNativeVersionIsNotSilentlyDowngraded() {
        LocalBackupCodec.encode(backup().copy(version = 2))
    }
    @Test(expected = IllegalArgumentException::class) fun danglingLibraryReferenceRejectedBeforeRestore() {
        LocalBackupCodec.encode(backup().copy(metadata = emptyList()))
    }
    @Test(expected = Exception::class) fun corruptGzipRejected() {
        TachiyomiBackupParser().parse(byteArrayOf(0x1f, 0x8b.toByte(), 0))
    }
    @Test fun emptyLibraryRoundTripIsValid() {
        val empty = LocalBackup(metadata = emptyList(), state = SyncPayload(deviceId = "local"))
        assertEquals(empty, LocalBackupCodec.decodeNative(LocalBackupCodec.encode(empty)))
    }
}
