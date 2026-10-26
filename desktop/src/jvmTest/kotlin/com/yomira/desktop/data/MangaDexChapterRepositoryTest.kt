package com.yomira.desktop.data

import com.yomira.core.network.NetworkConfiguration
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wire contract of the desktop reading transport. The JSON fixtures mirror the
 * documented MangaDex shapes (`/manga/{id}/feed`, `/at-home/server/{id}`) so a
 * renamed field fails here rather than in the reader at runtime.
 */
class MangaDexChapterRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MangaDexChapterRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = MangaDexChapterRepository(
            httpClient = OkHttpClient(),
            config = NetworkConfiguration(userAgent = "Yomira-Desktop-Test/0.4.0", mangaDexBaseUrl = server.url("/").toString()),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun item(source: String = "mangadex") = CatalogItem(
        id = "$source:abc",
        title = "One Piece",
        sourceId = source,
        remoteId = "abc",
    )

    @Test
    fun `chapters are filtered to english sorted by number and keep external entries`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "result": "ok",
                  "data": [
                    {
                      "id": "ch-2",
                      "attributes": {"chapter": "2.5", "title": "Ara bölüm", "translatedLanguage": "en", "pages": 18},
                      "relationships": [{"type": "scanlation_group", "attributes": {"name": "Group B"}}]
                    },
                    {
                      "id": "ch-1",
                      "attributes": {"chapter": "1", "title": null, "translatedLanguage": "en", "pages": 20},
                      "relationships": [{"type": "scanlation_group", "attributes": {"name": "Group A"}}]
                    },
                    {
                      "id": "ch-jp",
                      "attributes": {"chapter": "3", "translatedLanguage": "ja", "pages": 19},
                      "relationships": []
                    },
                    {
                      "id": "ch-ext",
                      "attributes": {"chapter": "4", "translatedLanguage": "en", "externalUrl": "https://scan.example/4"},
                      "relationships": []
                    }
                  ],
                  "total": 4
                }
                """.trimIndent(),
            ),
        )

        val chapters = repository.chapters(item())

        assertEquals(listOf("ch-1", "ch-2", "ch-ext"), chapters.map { it.id })
        assertEquals("Group A", chapters.first().group)
        assertEquals(2.5, chapters[1].number, 0.0)
        assertEquals("Ara bölüm", chapters[1].title)
        assertTrue(chapters[2].isReadable.not())
        assertEquals("https://scan.example/4", chapters[2].externalUrl)

        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().startsWith("/manga/abc/feed"))
        assertTrue(request.path.orEmpty().contains("translatedLanguage%5B%5D=en"))
        assertTrue(request.path.orEmpty().contains("order%5Bchapter%5D=asc"))
    }

    @Test
    fun `pages are built from the at-home manifest with a referer header`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "result": "ok",
                  "baseUrl": "https://uploads.example",
                  "chapter": {
                    "hash": "hash123",
                    "data": ["1.png", "2.png"],
                    "dataSaver": ["1-small.png"]
                  }
                }
                """.trimIndent(),
            ),
        )

        val pages = repository.pages(DesktopChapter(id = "ch-1", mangaId = "abc", number = 1.0))

        assertEquals(2, pages.size)
        assertEquals("https://uploads.example/data/hash123/1.png", pages[0].url)
        assertEquals("https://uploads.example/data/hash123/2.png", pages[1].url)
        assertEquals("https://mangadex.org/", pages[0].headers["Referer"])
        assertEquals(listOf(0, 1), pages.map { it.index })
    }

    @Test
    fun `data saver files are used when original pages are absent`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "result": "ok",
                  "baseUrl": "https://uploads.example/",
                  "chapter": {"hash": "h", "data": [], "dataSaver": ["small.jpg"]}
                }
                """.trimIndent(),
            ),
        )

        val pages = repository.pages(DesktopChapter(id = "ch", mangaId = "abc", number = 1.0))

        assertEquals("https://uploads.example/data-saver/h/small.jpg", pages.single().url)
    }

    @Test(expected = DesktopReadException::class)
    fun `an insecure page host is rejected`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"result": "ok", "baseUrl": "http://uploads.example", "chapter": {"hash": "h", "data": ["1.png"]}}
                """.trimIndent(),
            ),
        )
        repository.pages(DesktopChapter(id = "ch", mangaId = "abc", number = 1.0))
    }

    @Test(expected = DesktopReadException::class)
    fun `malformed feed json becomes a readable failure`() = runTest {
        server.enqueue(MockResponse().setBody("{not json"))
        repository.chapters(item())
    }

    @Test(expected = DesktopReadException::class)
    fun `http failures carry the status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        repository.chapters(item())
    }

    @Test(expected = DesktopReadException::class)
    fun `external chapters are never fetched page by page`() = runTest {
        repository.pages(
            DesktopChapter(
                id = "ch",
                mangaId = "abc",
                number = 1.0,
                externalUrl = "https://scan.example/1",
            ),
        )
    }

    @Test
    fun `non mangadex cards short circuit before any request`() = runTest {
        // No enqueued response: a network call here would fail the test.
        val chapters = repository.chapters(item(source = "anilist"))
        assertTrue(chapters.isEmpty())
        assertNull(server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS))
    }
}
