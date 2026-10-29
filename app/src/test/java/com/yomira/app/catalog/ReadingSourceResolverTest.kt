package com.yomira.app.catalog

import com.yomira.core.database.entity.ChapterEntity
import com.yomira.core.model.Manga
import com.yomira.domain.extension.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReadingSourceResolverTest {
    @Test fun `shared reader gate reranks cached chapters and maps logical number even after detail replaced the old id`() = runTest {
        // Home resume, detail read/continue and Updates Hub converge at this reader-route gate.
        for (detailAlreadyReplacedId in listOf(false, true)) {
            val dao = SourceMatchActivatorTest.FakeChapterDao()
            if (!detailAlreadyReplacedId) dao.upsert(ChapterEntity(id = "old-chapter", mangaId = "series", chapterNumber = 2.0,
                title = "Cached", url = "https://old.example.org/2"))
            val values = listOf(extension("short"), extension("complete-old"), extension("complete-fresh"))
            var installed = true
            val repository = object : ExtensionRepository {
                override suspend fun addRepository(indexUrl: String) = Unit
                override suspend fun removeRepository(indexUrl: String) = Unit
                override suspend fun repositories() = emptyList<String>()
                override suspend fun refresh() = values
                override suspend fun available() = values
                override suspend fun installed() = if (installed) values.map { InstalledExtension(it.packageName, 1, true, it) } else emptyList()
            }
            val now = 1_800_000_000_000L
            val bridge = object : SourceBridge {
                override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int) =
                    listOf(SourceManga("series", "Series", "/series"))
                override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) =
                    List(if (packageName == "short") 2 else 4) { index ->
                        SourceChapter("/$packageName/${index + 1}", "Chapter", (index + 1).toDouble(),
                            if (packageName == "complete-old") 100L else now)
                    }
                override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter) = emptyList<SourcePage>()
            }
            val matcher = SmartSourceMatcher(repository, bridge, SourceDiscovery { _, _ -> emptyList() },
                currentTimeMillis = { now }, nanoTime = { 0 })
            val result = ReadingSourceResolver(matcher, dao).resolve(Manga("series", "Series", sourceId = "metadata"),
                "old-chapter", ReaderPreferences(ReadingLanguage.ENGLISH),
                requestedNumber = if (detailAlreadyReplacedId) 2.0 else null)
            assertNotNull(result)
            val selected = dao.getById(result!!)!!
            assertEquals(2.0, selected.chapterNumber, 0.0)
            assertEquals("complete-fresh", SourceChapterLocation.decode(selected.url)?.packageName)
            assertEquals(4, dao.getByMangaId("series").size)
            assertNull(dao.getById("old-chapter"))
            assertNull(ReadingSourceResolver(matcher, dao).resolve(Manga("series", "Series", sourceId = "metadata"),
                "missing-chapter", ReaderPreferences(ReadingLanguage.ENGLISH)))
            installed = false
            assertNull(ReadingSourceResolver(matcher, dao).resolve(Manga("series", "Series", sourceId = "metadata"),
                result, ReaderPreferences(ReadingLanguage.ENGLISH)))
            assertEquals(4, dao.getByMangaId("series").size) // no installation: never read stale rows or erase history
            // A second call from the retry action must probe again, not cache the unavailable result.
            installed = true
            assertNotNull(ReadingSourceResolver(matcher, dao).resolve(Manga("series", "Series", sourceId = "metadata"),
                result, ReaderPreferences(ReadingLanguage.ENGLISH)))
        }
    }

    private fun extension(name: String) = ExtensionDescriptor(name, name, "en", false, "1", 1,
        "https://example.org/$name.apk", listOf(ExtensionSource(name, name, "en")))
}
