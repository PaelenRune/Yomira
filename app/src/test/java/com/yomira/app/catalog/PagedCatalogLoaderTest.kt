package com.yomira.app.catalog

import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.model.ContentRating
import com.yomira.core.model.ReadingMode
import com.yomira.core.network.AniListClient
import com.yomira.core.network.ComicVineClient
import com.yomira.core.network.MangaDexClient
import com.yomira.core.network.NetworkConfiguration
import com.yomira.core.network.OpenLibraryClient
import com.yomira.domain.classifier.ContentRatingClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end paging test for the endless engine: a page-2 request must hit
 * AniList with `page: 2`, MangaDex with `offset: 25`, OpenLibrary with `page=2`,
 * and every persisted western record must carry the spotlight genre marker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedCatalogLoaderTest {
    @Test
    fun `page two fans out to every provider and persists western marker`() = runTest {
        val aniList = MockWebServer()
        val mangaDex = MockWebServer()
        val openLibrary = MockWebServer()
        try {
            aniList.dispatcher = jsonDispatcher("""
                {"data":{"page":{"pageInfo":{"hasNextPage":true},"media":[{
                  "id":77,
                  "title":{"romaji":"Spider-Man","english":"Spider-Man"},
                  "countryOfOrigin":"US",
                  "format":"MANGA",
                  "genres":["Action"]
                }]}}}
            """.trimIndent())
            mangaDex.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path.orEmpty().contains("/statistics/manga") ->
                        MockResponse().setBody("""{"result":"ok","statistics":{}}""")
                    request.path.orEmpty().contains("originalLanguage") ->
                        jsonResponse("""{"result":"ok","data":[],"limit":25,"offset":25,"total":100}""")
                    else -> jsonResponse("""
                        {"result":"ok","data":[{
                          "id":"manga-1",
                          "attributes":{
                            "title":{"en":"Saga"},
                            "altTitles":[],
                            "description":{"en":"A space fantasy"},
                            "status":"ongoing",
                            "contentRating":"safe",
                            "tags":[],
                            "originalLanguage":"en"
                          }
                        }],"limit":25,"offset":25,"total":100}
                    """.trimIndent())
                }
            }
            openLibrary.dispatcher = jsonDispatcher("""
                {"numFound":120,"docs":[{
                  "key":"/works/OL123W",
                  "title":"Watchmen",
                  "author_name":["Alan Moore"],
                  "first_publish_year":1987,
                  "cover_i":456,
                  "subject":["Comic books, strips, etc."],
                  "publisher":["DC Comics"]
                }]}
            """.trimIndent())

            aniList.start()
            mangaDex.start()
            openLibrary.start()

            val dao = FakeMangaDao()
            val loader = PagedCatalogLoader(
                aniListClient = AniListClient(
                    OkHttpClient(),
                    config = NetworkConfiguration(aniListEndpoint = aniList.url("/").toString().trimEnd('/')),
                ),
                mangaDexClient = MangaDexClient(
                    OkHttpClient(),
                    config = NetworkConfiguration(mangaDexBaseUrl = mangaDex.url("/").toString().trimEnd('/')),
                ),
                comicVineClient = ComicVineClient(OkHttpClient()),
                openLibraryClient = OpenLibraryClient(
                    OkHttpClient(),
                    config = NetworkConfiguration(openLibraryBaseUrl = openLibrary.url("/").toString().trimEnd('/')),
                ),
                mangaDao = dao,
                classifier = ContentRatingClassifier(),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            )

            val page = loader.load(page = 2, adult = false)

            assertEquals(2, page.page)
            assertTrue(page.hasMore)
            assertEquals(3, page.items.size)
            assertEquals(setOf("anilist:77", "mangadex:manga-1", "openlibrary:OL123W"), dao.rows.map { it.id }.toSet())
            page.items.forEach { manga ->
                assertTrue("${manga.title} must be tagged for the western shelf", "Western Comic" in manga.genres)
            }
            assertTrue(dao.rows.any { it.title == "Spider-Man" && it.readingMode == ReadingMode.WESTERN_LTR })

            val aniRequest = aniList.takeRequest()
            assertTrue(aniRequest.body.readUtf8().contains("\"page\":2"))
            val dexRequests = generateSequence { mangaDex.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }.toList()
            assertTrue(dexRequests.any { it.requestUrl?.queryParameter("offset") == "25" })
            val openRequest = openLibrary.takeRequest()
            assertEquals("2", openRequest.requestUrl?.queryParameter("page"))
        } finally {
            aniList.shutdown()
            mangaDex.shutdown()
            openLibrary.shutdown()
        }
    }

    @Test
    fun `adult page keeps only explicit records`() = runTest {
        val aniList = MockWebServer()
        try {
            aniList.dispatcher = jsonDispatcher("""
                {"data":{"page":{"pageInfo":{"hasNextPage":true},"media":[
                  {"id":1,"title":{"romaji":"Safe One"},"genres":["Action"],"format":"MANGA","countryOfOrigin":"JP"},
                  {"id":2,"title":{"romaji":"Adult One"},"genres":["Hentai"],"format":"MANGA","countryOfOrigin":"JP"}
                ]}}}
            """.trimIndent())
            aniList.start()
            val dao = FakeMangaDao()
            val loader = PagedCatalogLoader(
                aniListClient = AniListClient(
                    OkHttpClient(),
                    config = NetworkConfiguration(aniListEndpoint = aniList.url("/").toString().trimEnd('/')),
                ),
                mangaDexClient = MangaDexClient(
                    OkHttpClient(),
                    config = NetworkConfiguration(mangaDexBaseUrl = aniList.url("/").toString().trimEnd('/')),
                ),
                comicVineClient = ComicVineClient(OkHttpClient()),
                openLibraryClient = OpenLibraryClient(OkHttpClient()),
                mangaDao = dao,
                classifier = ContentRatingClassifier(),
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            )

            val page = loader.load(page = 1, adult = true)

            assertEquals(listOf("anilist:2"), page.items.map { it.id })
            assertEquals(ContentRating.EXPLICIT, page.items.single().contentRating)
            assertTrue(dao.rows.none { it.id == "anilist:1" })
        } finally {
            aniList.shutdown()
        }
    }

    private fun jsonDispatcher(payload: String): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse = jsonResponse(payload)
    }

    private fun jsonResponse(payload: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(payload)

    private class FakeMangaDao(
        val rows: MutableList<MangaEntity> = mutableListOf(),
    ) : MangaDao {
        override suspend fun updateReadingMode(mangaId: String, mode: ReadingMode) {
            val index = rows.indexOfFirst { it.id == mangaId }
            if (index >= 0) rows[index] = rows[index].copy(readingMode = mode)
        }

        override fun observeRecentlyUpdated(): Flow<List<MangaEntity>> = flowOf(rows.toList())
        override fun observeAll(): Flow<List<MangaEntity>> = flowOf(rows.toList())
        override fun observeById(id: String): Flow<MangaEntity?> = flowOf(rows.firstOrNull { it.id == id })
        override fun observeBySource(sourceId: String): Flow<List<MangaEntity>> =
            flowOf(rows.filter { it.sourceId == sourceId })

        override suspend fun getById(id: String): MangaEntity? = rows.firstOrNull { it.id == id }
        override suspend fun count(): Int = rows.size
        override suspend fun existsByTitle(title: String): Boolean =
            rows.any { it.title.equals(title, ignoreCase = true) }

        override suspend fun findByTitleOrPattern(title: String, elementPattern: String): MangaEntity? =
            rows.firstOrNull { stored ->
                stored.title.equals(title, ignoreCase = true) ||
                    stored.altTitles.any { it.equals(title, ignoreCase = true) }
            }

        override fun observeByRawQuery(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<MangaEntity>> =
            flowOf(rows.toList())

        override suspend fun upsert(manga: MangaEntity) {
            rows.removeAll { it.id == manga.id }
            rows += manga
        }

        override suspend fun upsertAll(manga: List<MangaEntity>) { manga.forEach { upsert(it) } }
        override suspend fun deleteById(id: String) { rows.removeAll { it.id == id } }
    }
}
