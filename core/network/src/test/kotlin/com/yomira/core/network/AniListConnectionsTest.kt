package com.yomira.core.network

import com.yomira.core.model.RelationRole
import com.yomira.core.model.AdaptationKind
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListConnectionsTest {

    @Test fun `relations carry franchise roles and recommendations keep scores`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"media":{
                  "id":101093,
                  "relations":{"edges":[
                    {"relationType":"PREQUEL","node":{"id":431,"type":"MANGA","title":{"romaji":"Ore dake Level Up na Ken","english":"Solo Leveling"},"coverImage":{"large":"https://img/cover-431.jpg"},"bannerImage":"https://img/banner-431.jpg"}},
                    {"relationType":"SPIN_OFF","node":{"id":99001,"type":"MANGA","title":{"romaji":"Solo Leveling: Ragnarok","english":"Solo Leveling: Ragnarok"},"coverImage":{"large":"https://img/cover-99001.jpg"}}},
                    {"relationType":"ADAPTATION","node":{"id":70001,"type":"ANIME","title":{"romaji":"Ore dake Level Up na Ken"}}},
                    {"relationType":"CHARACTER","node":null}
                  ]},
                  "recommendations":{"nodes":[
                    {"mediaRecommendation":{"id":105778,"title":{"romaji":"Jeonjijeok Dokja","english":"Omniscient Reader's Viewpoint"},"coverImage":{"extraLarge":"https://img/cover-105778.jpg"},"averageScore":86}},
                    {"mediaRecommendation":null}
                  ]}
                }}}
            """.trimIndent()))
            val client = AniListClient(OkHttpClient(), config = NetworkConfiguration(aniListEndpoint = server.url("/graphql").toString()))

            val connections = client.connections(101093)

            // The anime adaptation and the empty character node never reach the shelf.
            assertEquals(listOf("anilist:431", "anilist:99001"), connections.related.map { it.id })
            val prequel = connections.related.first()
            assertEquals(RelationRole.PREQUEL, prequel.role)
            // English display title wins, exactly like the shelf title rule.
            assertEquals("Solo Leveling", prequel.title)
            assertEquals("https://img/cover-431.jpg", prequel.coverUrl)
            assertEquals("https://img/banner-431.jpg", prequel.bannerUrl)
            assertEquals(RelationRole.SPIN_OFF, connections.related[1].role)
            assertEquals("anilist:70001", connections.adaptations.single().id)
            assertEquals(AdaptationKind.ANIME, connections.adaptations.single().kind)

            val recommended = connections.recommended.single()
            assertEquals("anilist:105778", recommended.id)
            assertEquals("Omniscient Reader's Viewpoint", recommended.title)
            assertEquals(8.6, recommended.score!!, 0.0001)
            assertEquals("https://img/cover-105778.jpg", recommended.coverUrl)
            assertNull(recommended.bannerUrl)

            // The GraphQL document must actually ask for both showcases.
            val sent = server.takeRequest().body.readUtf8()
            assertTrue(sent.contains("relations"))
            assertTrue(sent.contains("recommendations"))
        }
    }

    @Test fun `explicit tags and live action labels classify without treating anime drama as live action`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"media":{"relations":{"edges":[
                  {"relationType":"ADAPTATION","node":{"id":1,"type":"ANIME","format":"OVA","tags":[{"name":" explicit "}],"title":{"english":"Explicit OVA"}}},
                  {"relationType":"ADAPTATION","node":{"id":2,"genres":["Live-Action"],"title":{"english":"Live action"}}},
                  {"relationType":"ADAPTATION","node":{"id":3,"genres":["K-Drama"],"title":{"english":"Korean drama"}}},
                  {"relationType":"ADAPTATION","node":{"id":4,"genres":["Drama"],"title":{"english":"Drama"}}},
                  {"relationType":"ADAPTATION","node":{"id":5,"type":"ANIME","format":"TV","genres":["Drama"],"title":{"english":"Animated drama"}}}
                ]}}}}
            """.trimIndent()))
            val client = AniListClient(OkHttpClient(), config = NetworkConfiguration(aniListEndpoint = server.url("/graphql").toString()))

            assertEquals(
                listOf(AdaptationKind.HENTAI_OVA, AdaptationKind.LIVE_ACTION, AdaptationKind.LIVE_ACTION,
                    AdaptationKind.LIVE_ACTION, AdaptationKind.ANIME),
                client.connections(1).adaptations.map { it.kind },
            )
        }
    }

    @Test fun `unknown media returns empty showcases instead of failing`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"media":null}}"""))
            val client = AniListClient(OkHttpClient(), config = NetworkConfiguration(aniListEndpoint = server.url("/graphql").toString()))
            val connections = client.connections(1)
            assertEquals(emptyList<String>(), connections.related)
            assertEquals(emptyList<String>(), connections.recommended)
            assertTrue(connections.adaptations.isEmpty())
        }
    }

    @Test fun `watchable relations preserve metadata and classify without leaking manga`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"media":{"relations":{"edges":[
                  {"relationType":"ADAPTATION","node":{"id":1,"type":"ANIME","format":"TV","episodes":12,"startDate":{"year":2024},"averageScore":87,"title":{"english":"Anime"},"coverImage":{"extraLarge":"https://img/1.jpg"},"bannerImage":"https://img/banner.jpg"}},
                  {"relationType":"ADAPTATION","node":{"id":2,"type":"ANIME","format":"OVA","genres":["Hentai"],"title":{"romaji":"Adult OVA"}}},
                  {"relationType":"ADAPTATION","node":{"id":3,"type":"ANIME","format":"ONA","countryOfOrigin":"CN","title":{"romaji":"Donghua"}}},
                  {"relationType":"ADAPTATION","node":{"id":4,"type":"ANIME","format":"ONA","countryOfOrigin":"JP","title":{"romaji":"Web anime"}}},
                  {"relationType":"ADAPTATION","node":{"id":5,"type":"ANIME","format":"SPECIAL","title":{"romaji":"Special"}}},
                  {"relationType":"ADAPTATION","node":{"id":6,"type":"MANGA","title":{"romaji":"Manga adaptation"}}},
                  {"relationType":"ADAPTATION","node":{"id":7,"format":"DRAMA","title":{"romaji":"Drama"}}},
                  {"relationType":"ADAPTATION","node":{"id":8,"type":"ANIME","format":"OVA","tags":[{"name":"Adult","isAdult":true}],"title":{"romaji":"Tagged adult"}}},
                  {"relationType":"OTHER","node":{"id":9,"type":"anime","format":"MOVIE","tags":[{"name":"hentai"}],"title":{"romaji":"Tagged hentai"}}},
                  {"relationType":"OTHER","node":{"id":10,"type":"ANIME","format":"OVA","isAdult":true,"title":{"romaji":"Adult flag"}}},
                  {"relationType":"OTHER","node":{"id":11,"type":"ANIME","episodes":0,"startDate":{"year":0},"title":{"romaji":"Unknown format"}}},
                  {"relationType":"ADAPTATION","node":{"id":1,"type":"ANIME","title":{"romaji":"Duplicate"}}},
                  {"relationType":"ADAPTATION","node":{"type":"ANIME","title":{"romaji":"Missing id"}}},
                  {"relationType":"ADAPTATION","node":{"id":12,"type":"ANIME","title":{"romaji":" "}}},
                  {"relationType":"ADAPTATION","node":null}
                ]}}}}
            """.trimIndent()))
            val client = AniListClient(OkHttpClient(), config = NetworkConfiguration(aniListEndpoint = server.url("/graphql").toString()))

            val connections = client.connections(1)

            assertEquals(listOf("anilist:6"), connections.related.map { it.id })
            assertEquals(listOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11).map { "anilist:$it" }, connections.adaptations.map { it.id })
            assertEquals(listOf(AdaptationKind.ANIME, AdaptationKind.HENTAI_OVA, AdaptationKind.DONGHUA,
                AdaptationKind.ANIME, AdaptationKind.SPECIAL, AdaptationKind.LIVE_ACTION,
                AdaptationKind.HENTAI_OVA, AdaptationKind.HENTAI_OVA, AdaptationKind.HENTAI_OVA,
                AdaptationKind.ANIME), connections.adaptations.map { it.kind })
            val anime = connections.adaptations.first()
            assertEquals("Anime", anime.title)
            assertEquals("TV", anime.format)
            assertEquals(12, anime.episodes)
            assertEquals(2024, anime.releaseYear)
            assertEquals(8.7, anime.score!!, 0.0001)
            assertEquals("https://img/1.jpg", anime.coverUrl)
            assertEquals("https://img/banner.jpg", anime.bannerUrl)
            assertNull(connections.adaptations.last().episodes)
            assertNull(connections.adaptations.last().releaseYear)
            val sent = server.takeRequest().body.readUtf8()
            listOf("format", "status", "episodes", "genres", "tags", "isAdult", "countryOfOrigin", "startDate", "averageScore").forEach {
                assertTrue("Query must request $it", sent.contains(it))
            }
        }
    }
}
