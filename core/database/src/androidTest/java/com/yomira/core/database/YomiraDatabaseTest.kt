package com.yomira.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yomira.core.database.entity.ChapterEntity
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.database.entity.MetadataCacheEntity
import com.yomira.core.database.entity.SyncStateEntity
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YomiraDatabaseTest {
    private lateinit var database: YomiraDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            YomiraDatabase::class.java,
        ).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun parentUpsertPreservesChaptersAndDeleteCascades() = runTest {
        val manga = manga()
        database.mangaDao().upsert(manga)
        database.chapterDao().upsert(chapter())
        database.mangaDao().upsert(manga.copy(title = "Updated"))
        assertEquals(listOf(chapter()), database.chapterDao().getByMangaId(manga.id))
        assertEquals("Updated", database.mangaDao().observeById(manga.id).first()?.title)
        database.mangaDao().deleteById(manga.id)
        assertEquals(emptyList<ChapterEntity>(), database.chapterDao().getByMangaId(manga.id))
    }

    @Test
    fun chapterIndexIsOrderedNumericallyAndCanBeReplaced() = runTest {
        database.mangaDao().upsert(manga())
        database.chapterDao().upsertAll(listOf(chapter("s:c10", 10.0), chapter("s:c2", 2.0)))
        assertEquals(listOf(2.0, 10.0), database.chapterDao().observeByMangaId("s:m").first().map { it.chapterNumber })
        database.chapterDao().replaceForManga("s:m", listOf(chapter("s:c3", 3.5)))
        assertEquals(listOf(chapter("s:c3", 3.5)), database.chapterDao().getByMangaId("s:m"))
    }

    @Test
    fun invalidReplacementDoesNotDeleteExistingChapters() = runTest {
        database.mangaDao().upsert(manga())
        database.chapterDao().upsert(chapter())
        var rejected = false
        try {
            database.chapterDao().replaceForManga("s:m", listOf(chapter().copy(mangaId = "other")))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertEquals(true, rejected)
        assertEquals(listOf(chapter()), database.chapterDao().getByMangaId("s:m"))
    }

    @Test
    fun cacheKeysAreSourceScopedAndExpiryIsExclusive() = runTest {
        val cache = database.metadataCacheDao()
        val first = MetadataCacheEntity("source-a", "manga:1", "{}", Date(100), Date(200))
        val second = first.copy(sourceId = "source-b", expiresAt = Date(300))
        cache.upsert(first)
        cache.upsert(second)
        assertEquals(first, cache.getValid("source-a", "manga:1", Date(199)))
        assertNull(cache.getValid("source-a", "manga:1", Date(200)))
        assertEquals(1, cache.deleteExpired(Date(200)))
        assertEquals(second, cache.getValid("source-b", "manga:1", Date(200)))
    }

    @Test
    fun syncCompareAndSetRejectsStaleWriters() = runTest {
        val sync = database.syncDao()
        sync.upsert(SyncStateEntity(deviceId = "device", version = 1))
        assertEquals(1, sync.advanceVersion("device", 1, 2, Date(200)))
        assertEquals(0, sync.advanceVersion("device", 1, 3, Date(300)))
        assertEquals(0, sync.advanceVersion("device", 2, 1, Date(300)))
        assertEquals(SyncStateEntity("device", Date(200), 2), sync.observeByDeviceId("device").first())
    }

    private fun manga() = MangaEntity(id = "s:m", title = "Title", sourceId = "s")

    @Test
    fun readerProgressSurvivesChapterRefreshAndMarksSyncDirty() = runTest {
        database.mangaDao().upsert(manga())
        database.chapterDao().upsert(chapter())
        val progress = com.yomira.core.database.entity.ReaderProgressEntity("s:c1", "s:m", 0, 13, 45, 100)
        database.chapterDao().saveProgress(progress)
        database.chapterDao().replaceForManga("s:m", listOf(chapter()))
        assertEquals(progress, database.chapterDao().getProgress("s:c1"))
        assertEquals(progress, database.syncDao().latestReaderProgress("s:m"))
        database.syncDao().markReaderDirty("local-reader")
        database.syncDao().markReaderDirty("local-reader")
        assertEquals(2L, database.syncDao().getByDeviceId("local-reader")?.version)
        assertNull(database.syncDao().getByDeviceId("local-reader")?.lastSyncedAt)
    }

    private fun chapter(id: String = "s:c1", number: Double = 1.0) = ChapterEntity(
        id = id,
        mangaId = "s:m",
        chapterNumber = number,
        url = "https://example.org/$id",
    )
}
