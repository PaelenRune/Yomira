package com.yomira.feature.reader

import com.yomira.core.database.entity.ChapterEntity
import com.yomira.domain.extension.*
import java.util.Optional
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class MatchedSourceReaderTest {
    @Test fun matchedChapterUsesSelectedPackageAndOriginalChapterIdNotCatalogSource() = runTest {
        var requested: Triple<String, String, String>? = null
        val bridge = object : SourceBridge {
            override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int) = emptyList<SourceManga>()
            override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) = emptyList<SourceChapter>()
            override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter): List<SourcePage> {
                requested = Triple(packageName, sourceId, chapter.id)
                return listOf(SourcePage(0, "https://example.org/page.jpg"))
            }
        }
        val extensions = object : ExtensionRepository {
            override suspend fun addRepository(indexUrl: String) = Unit
            override suspend fun removeRepository(indexUrl: String) = Unit
            override suspend fun repositories() = emptyList<String>()
            override suspend fun refresh() = emptyList<ExtensionDescriptor>()
            override suspend fun available() = emptyList<ExtensionDescriptor>()
            override suspend fun installed() = listOf(InstalledExtension("chosen.extension", 1, true))
        }
        val repository = ReaderPageRepository(OkHttpClient(), Optional.of(bridge), Optional.of(extensions))
        val location = SourceChapterLocation("chosen.extension", "english-source", "/chapter/1")
        val chapter = ChapterEntity("catalog:1:${location.encode()}", "catalog:1", 1.0, url = location.encode())
        assertEquals(1, repository.pages("anilist", chapter).size)
        assertEquals(Triple("chosen.extension", "english-source", "/chapter/1"), requested)
    }
}
