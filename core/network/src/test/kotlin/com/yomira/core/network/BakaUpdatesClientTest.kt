package com.yomira.core.network

import com.yomira.core.model.MangaStatus
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BakaUpdatesClientTest {
    @Test
    fun `discovery requests rating ranked page and preserves rich metadata`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"total_hits":51,"results":[{"record":{
                  "series_id":42,"title":"Series","type":"Manga","status":"Complete",
                  "associated":[{"title":"別名"},{"title":"Another Title"}],
                  "authors":[{"name":"Writer One","type":"Author"},{"name":"Writer Two","type":"Author"},
                             {"name":"Artist","type":"Artist"}],
                  "genres":[{"genre":"Adventure"},{"genre":"Fantasy"}],
                  "bayesian_rating":9.4,"year":"2020"
                }}]}
            """.trimIndent()))
            val page = client(server).discover(2, 25)

            assertTrue(page.hasMore)
            val record = page.items.single()
            assertEquals(listOf("別名", "Another Title"), record.altTitles["associated"])
            assertEquals("Writer One, Writer Two", record.author)
            assertEquals("Artist", record.artist)
            assertEquals(listOf("Adventure", "Fantasy"), record.genres)
            assertEquals(MangaStatus.COMPLETED, record.status)
            assertEquals(9.4, record.score!!, 0.001)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/series/search", request.path)
            assertEquals("""{"stype":"title","orderby":"rating","page":2,"perpage":25}""", request.body.readUtf8())
        }
    }

    @Test
    fun `empty final API page does not trigger HTML fallback`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"total_hits":25,"results":[]}"""))
            val page = client(server).discover(2, 25)
            assertTrue(page.items.isEmpty())
            assertFalse(page.hasMore)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `unavailable API falls back to paged public catalogue`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("""
                <a href="/series/abc/series-title">Series Title</a>
                <a href="/series.html?page=3" rel="next">Next</a>
            """.trimIndent()))
            val page = client(server).discover(2, 25)
            assertEquals("abc", page.items.single().remoteId)
            assertTrue(page.hasMore)
            server.takeRequest()
            val fallback = server.takeRequest()
            assertEquals("2", fallback.requestUrl?.queryParameter("page"))
            assertEquals("rating", fallback.requestUrl?.queryParameter("orderby"))
        }
    }

    @Test(expected = MetadataClientException::class)
    fun `failed API and HTML fallback propagate provider failure`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(503))
            client(server).discover(1, 25)
        }
    }

    private fun client(server: MockWebServer): BakaUpdatesClient {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return BakaUpdatesClient(
            OkHttpClient(),
            config = NetworkConfiguration(mangaUpdatesBaseUrl = baseUrl, mangaUpdatesWebUrl = baseUrl),
        )
    }
}
