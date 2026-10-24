package com.yomira.core.network

import com.yomira.core.model.RelationRole
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

    @Test fun `unknown media returns empty showcases instead of failing`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"media":null}}"""))
            val client = AniListClient(OkHttpClient(), config = NetworkConfiguration(aniListEndpoint = server.url("/graphql").toString()))
            val connections = client.connections(1)
            assertEquals(emptyList<String>(), connections.related)
            assertEquals(emptyList<String>(), connections.recommended)
        }
    }
}