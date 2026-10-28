package com.yomira.app.catalog

import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.entity.ChapterEntity
import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.domain.extension.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SourceMatchActivatorTest {
    @Test fun matchedChaptersAppearInDetailFlowWithStableSourceRouting() = runTest {
        val dao = FakeChapterDao()
        val activator = SourceMatchActivator(dao)
        val result = ready("source-one")
        activator.activate("catalog:1", result)
        val rows = dao.observeByMangaId("catalog:1").first()
        assertEquals(2, rows.size)
        assertEquals("Chapter one", rows.first().title)
        assertEquals(SourceChapterLocation("extension", "source-one", "/chapter/1?q=ü"), SourceChapterLocation.decode(rows.first().url))
        val firstId = rows.first().id
        activator.activate("catalog:1", result)
        assertEquals(firstId, dao.observeByMangaId("catalog:1").first().first().id)
        activator.activate("catalog:2", result)
        assertEquals(4, dao.rows.value.size)
        assertEquals(4, dao.rows.value.map { it.id }.distinct().size)
        activator.activate("catalog:1", ready("source-two"))
        assertEquals(2, dao.observeByMangaId("catalog:1").first().size)
        assertTrue(dao.observeByMangaId("catalog:1").first().all { SourceChapterLocation.decode(it.url)?.sourceId == "source-two" })
    }

    @Test fun emptyMatchDoesNotEraseExistingChapterIndex() = runTest {
        val dao = FakeChapterDao()
        val activator = SourceMatchActivator(dao)
        activator.activate("catalog:1", ready("one"))
        activator.activate("catalog:1", ready("two").copy(chapters = emptyList()))
        assertEquals(2, dao.observeByMangaId("catalog:1").first().size)
    }

    private fun ready(sourceId: String): SmartMatchResult.Ready {
        val source = ExtensionSource(sourceId, "English source", "en")
        val extension = ExtensionDescriptor("extension", "Extension", "en", false, "1", 1, "https://example.org/a.apk", listOf(source))
        val selected = SourceCandidate(extension, source, SourceManga("manga", "Title", "/title"), 2, null)
        return SmartMatchResult.Ready(selected, listOf(
            SourceChapter("/chapter/1?q=ü", "Chapter one", 1.0, 100),
            SourceChapter("/chapter/2", "Chapter two", 2.0, 200),
        ), emptyList())
    }

    internal class FakeChapterDao : ChapterDao {
        val rows = MutableStateFlow<List<ChapterEntity>>(emptyList())
        override suspend fun getProgress(chapterId: String): ReaderProgressEntity? = null
        override suspend fun saveProgress(progress: ReaderProgressEntity) = Unit
        override fun observeByMangaId(mangaId: String): Flow<List<ChapterEntity>> = rows.map { list -> list.filter { it.mangaId == mangaId } }
        override suspend fun getByMangaId(mangaId: String) = rows.value.filter { it.mangaId == mangaId }
        override suspend fun getById(id: String) = rows.value.firstOrNull { it.id == id }
        override suspend fun upsert(chapter: ChapterEntity) { rows.value = rows.value.filterNot { it.id == chapter.id } + chapter }
        override suspend fun upsertAll(chapters: List<ChapterEntity>) { chapters.forEach { upsert(it) } }
        override suspend fun deleteByMangaId(mangaId: String) { rows.value = rows.value.filterNot { it.mangaId == mangaId } }
    }
}
