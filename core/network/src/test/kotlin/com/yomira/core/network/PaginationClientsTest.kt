package com.yomira.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pagination contract for every infinite-feed provider: AniList page 2 must
 * send `page: 2`, MangaDex page 2 must send `offset: 25`, ComicVine page 2 must
 * send `offset: 25` and OpenLibrary page 2 must send `page=2`.
 */
class PaginationClientsTest {
    @Test
    fun `anilist discover sends page and perPage and parses continuation`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"page":{
                  "pageInfo":{"currentPage":2,"hasNextPage":true,"total":120},
                  "media":[{
                    "id":77,
                    "title":{"romaji":"Spider-Man","english":"Spider-Man"},
                    "description":"A hero",
                    "coverImage":{"extraLarge":"https://example.org/spider.jpg"},
                    "averageScore":88,
                    "status":"RELEASING",
                    "genres":["Action"],
                    "format":"MANGA",
                    "countryOfOrigin":"US",
                    "startDate":{"year":2018}
                  }]
                }}}
            """.trimIndent()))
            val client = AniListClient(
                OkHttpClient(),
                config = NetworkConfiguration(aniListEndpoint = server.url("/").toString().trimEnd('/')),
            )

            val page = client.discover(page = 2, perPage = 25)

            assertEquals(2, page.page)
            assertTrue(page.hasNextPage)
            assertEquals("Spider-Man", page.records.single().title)
            assertEquals("Comic", page.records.single().publicationType)
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertTrue("Expected page 2 in GraphQL body: $body", body.contains("\"page\":2"))
            assertTrue("Expected perPage 25 in GraphQL body: $body", body.contains("\"perPage\":25"))
            assertTrue("Expected isAdult false in GraphQL body: $body", body.contains("\"isAdult\":false"))
        }
    }

    @Test
    fun `mangadex discover maps page to offset and exposes total`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"result":"ok","data":[{
                  "id":"manga-1",
                  "attributes":{
                    "title":{"en":"Saga"},
                    "altTitles":[],
                    "description":{"en":"A space fantasy"},
                    "status":"ongoing",
                    "year":2012,
                    "contentRating":"safe",
                    "tags":[],
                    "originalLanguage":"en"
                  }
                }],"limit":25,"offset":25,"total":100}
            """.trimIndent()))
            server.enqueue(MockResponse().setBody("""{"result":"ok","statistics":{}}"""))
            val client = MangaDexClient(
                OkHttpClient(),
                config = NetworkConfiguration(mangaDexBaseUrl = server.url("/").toString().trimEnd('/')),
            )

            val page = client.discover(page = 2, perPage = 25)

            assertEquals(1, page.items.size)
            assertEquals("Saga", page.items.single().title)
            assertEquals("Comic", page.items.single().publicationType)
            assertTrue(page.hasMore)
            val request = server.takeRequest().requestUrl!!
            assertEquals("25", request.queryParameter("offset"))
            assertEquals("25", request.queryParameter("limit"))
        }
    }

    @Test
    fun `mangadex western feed requests English originals only`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"result":"ok","data":[],"limit":25,"offset":0,"total":0}"""))
            val client = MangaDexClient(
                OkHttpClient(),
                config = NetworkConfiguration(mangaDexBaseUrl = server.url("/").toString().trimEnd('/')),
            )

            val page = client.western(page = 1, perPage = 25)

            assertFalse(page.hasMore)
            val request = server.takeRequest().requestUrl!!
            assertEquals("en", request.queryParameter("originalLanguage[]"))
            assertTrue(request.queryParameterValues("contentRating[]").containsAll(listOf("safe", "suggestive")))
        }
    }

    @Test
    fun `comicvine discover sends offset and reports total pages`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"status_code":1,"number_of_total_results":100,"results":[{
                  "id":12,"name":"Batman","start_year":"2016",
                  "publisher":{"name":"DC Comics"},
                  "image":{"original_url":"https://example.org/batman.jpg"}
                }]}
            """.trimIndent()))
            val client = ComicVineClient(
                OkHttpClient(),
                config = NetworkConfiguration(
                    comicVineApiKey = "test-key",
                    comicVineBaseUrl = server.url("/").toString().trimEnd('/'),
                ),
            )

            val page = client.discover(page = 2, perPage = 25)

            assertEquals("Batman", page.items.single().title)
            assertTrue(page.hasMore)
            val request = server.takeRequest().requestUrl!!
            assertEquals("25", request.queryParameter("offset"))
            assertEquals("25", request.queryParameter("limit"))
            assertEquals("volumes", request.pathSegments.last { it.isNotEmpty() })
        }
    }

    @Test
    fun `openlibrary discover sends 1-based page and maps covers`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"numFound":120,"docs":[{
                  "key":"/works/OL123W",
                  "title":"Watchmen",
                  "author_name":["Alan Moore"],
                  "first_publish_year":1987,
                  "cover_i":456,
                  "subject":["Comic books, strips, etc.","Superheroes"],
                  "publisher":["DC Comics"]
                }]}
            """.trimIndent()))
            val client = OpenLibraryClient(
                OkHttpClient(),
                config = NetworkConfiguration(openLibraryBaseUrl = server.url("/").toString().trimEnd('/')),
            )

            val page = client.discover(page = 2, perPage = 25)

            val record = page.items.single()
            assertEquals("Watchmen", record.title)
            assertEquals("OL123W", record.remoteId)
            assertEquals("https://covers.openlibrary.org/b/id/456-L.jpg", record.coverUrl)
            assertEquals("Comic", record.publicationType)
            assertTrue(page.hasMore)
            val request = server.takeRequest().requestUrl!!
            assertEquals("2", request.queryParameter("page"))
            assertEquals("25", request.queryParameter("limit"))
        }
    }
}
