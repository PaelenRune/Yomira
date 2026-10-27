package com.yomira.core.network

import com.yomira.core.model.ExternalRating
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class FallbackClientsTest {
    @Test fun canonicalAndAlternativeTitlesAreCollectedWithoutDuplicates() {
        val record = RemoteMetadata("kitsu", "1", "One Piece", altTitles = mapOf("en" to listOf("ONE PIECE", "ワンピース")))
        assertEquals(listOf("One Piece", "ワンピース"), record.allTitles())
    }

    @Test fun englishTitleParticipatesInIdentityMatching() {
        val record = RemoteMetadata("anilist", "1", "Shingeki no Kyojin", englishTitle = "Attack on Titan")
        assertEquals(listOf("Shingeki no Kyojin", "Attack on Titan"), record.allTitles())
    }

    @Test fun aniListCapturesEnglishTitleOverProviderRomaji() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"page":{"media":[{
                  "id":431,
                  "title":{"romaji":"Shingeki no Kyojin","english":"Attack on Titan","native":"進撃の巨人"},
                  "genres":["Action","Drama"]
                }]}}}
            """.trimIndent()))
            val client = AniListClient(OkHttpClient(), config = NetworkConfiguration(aniListEndpoint = server.url("/graphql").toString()))
            val record = client.search("Attack on Titan", 5).single()
            assertEquals("Shingeki no Kyojin", record.title)
            assertEquals("Attack on Titan", record.englishTitle)
            assertTrue(record.allTitles().containsAll(listOf("Shingeki no Kyojin", "Attack on Titan", "進撃の巨人")))
        }
    }

    @Test fun mangaDexCapturesEnglishTitleFromTitleLocale() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":[{"id":"a1","attributes":{
                  "title":{"ja":"進撃の巨人","en":"Attack on Titan"},
                  "status":"finished","year":2009
                }}]}
            """.trimIndent()))
            server.enqueue(MockResponse().setBody("""{"statistics":{}}"""))
            val client = MangaDexClient(OkHttpClient(), config = NetworkConfiguration(mangaDexBaseUrl = server.url("/").toString().trimEnd('/')))
            val record = client.search("Attack on Titan", 5).single()
            // pickTitle prefers `en`, and englishTitle exposes it for the display rule.
            assertEquals("Attack on Titan", record.title)
            assertEquals("Attack on Titan", record.englishTitle)
        }
    }

    @Test fun kitsuCategoriesStayScopedAndExplicitEvidenceIsPreserved() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":[
                  {"id":"1","attributes":{"canonicalTitle":"One Piece","averageRating":"85.5"},"relationships":{"categories":{"data":[{"id":"a"}]}}},
                  {"id":"2","attributes":{"canonicalTitle":"Other","ageRating":"R18"},"relationships":{"categories":{"data":[{"id":"b"}]}}}
                ],"included":[
                  {"id":"a","type":"categories","attributes":{"title":"Adventure"}},
                  {"id":"b","type":"categories","attributes":{"title":"Hentai"}}
                ]}
            """.trimIndent()))
            val client = KitsuClient(OkHttpClient(), config = NetworkConfiguration(kitsuBaseUrl = server.url("/").toString().trimEnd('/')))
            val result = client.search("One Piece", 5)
            assertEquals(listOf("Adventure"), result[0].tags)
            assertEquals(8.55, result[0].score!!, 0.0001)
            assertEquals(ExternalRating.PORNOGRAPHIC, result[1].contentRating)
            assertEquals("One Piece", server.takeRequest().requestUrl!!.queryParameter("filter[text]"))
        }
    }

    @Test fun comicVineWithoutCredentialsDoesNotSendRequests() = runTest {
        val client = ComicVineClient(OkHttpClient())
        assertFalse(client.isAvailable)
        assertTrue(client.search("Spider-Man", 5).isEmpty())
    }

    @Test fun kitsuUsesOriginalLandscapeAndPosterIncludingAdultRecords() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":[{"id":"18","attributes":{
                  "canonicalTitle":"Adult Series","ageRating":"R18",
                  "posterImage":{"original":"https://img.test/poster-original.jpg","large":"https://img.test/poster-large.jpg"},
                  "coverImage":{"original":"https://img.test/banner-original.jpg","large":"https://img.test/banner-large.jpg"}
                }}]}
            """.trimIndent()))
            val client = KitsuClient(OkHttpClient(), config = NetworkConfiguration(kitsuBaseUrl = server.url("/").toString().trimEnd('/')))
            val record = client.search("Adult Series", 5).single()
            assertEquals("https://img.test/poster-original.jpg", record.coverUrl)
            assertEquals("https://img.test/banner-original.jpg", record.bannerUrl)
            assertEquals(ExternalRating.PORNOGRAPHIC, record.contentRating)
        }
    }

    @Test fun kitsuUsesLargeLandscapeWhenOriginalIsBlank() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"id":"1","attributes":{"canonicalTitle":"Webtoon",
                  "coverImage":{"original":"","large":"https://img.test/banner-large.jpg"}
                }}}
            """.trimIndent()))
            val client = KitsuClient(OkHttpClient(), config = NetworkConfiguration(kitsuBaseUrl = server.url("/").toString().trimEnd('/')))
            assertEquals("https://img.test/banner-large.jpg", client.findById("1")!!.bannerUrl)
        }
    }

    @Test fun comicVineMapsVolumeAndEncodesSearch() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"status_code":1,"results":[{"id":12,"name":"Spider-Man","description":"<p>A hero</p>","start_year":"1963","count_of_issues":100,"publisher":{"name":"Marvel"},"image":{"original_url":"https://example.org/cover.jpg"}}]}
            """.trimIndent()))
            val config = NetworkConfiguration(comicVineApiKey = "test-key", comicVineBaseUrl = server.url("/").toString().trimEnd('/'))
            val record = ComicVineClient(OkHttpClient(), config = config).search("Spider-Man & friends", 5).single()
            assertEquals("A hero", record.description)
            assertEquals("Marvel", record.publisher)
            assertEquals(1963, record.year)
            val request = server.takeRequest().requestUrl!!
            assertEquals("Spider-Man & friends", request.queryParameter("query"))
            assertEquals("volume", request.queryParameter("resources"))
        }
    }

    @Test fun comicVineApiErrorIsNotTreatedAsSuccessfulEmptyMetadata() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status_code":100,"results":[]}"""))
            val config = NetworkConfiguration(comicVineApiKey = "test", comicVineBaseUrl = server.url("/").toString().trimEnd('/'))
            try { ComicVineClient(OkHttpClient(), config = config).search("Spider-Man", 5); fail("Expected API error") }
            catch (_: MetadataClientException) { }
        }
    }
}
