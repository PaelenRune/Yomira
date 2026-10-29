package com.yomira.data.metadata

import com.yomira.core.model.AdaptationKind
import com.yomira.core.model.Manga
import com.yomira.core.network.AniListClient
import com.yomira.core.network.MetadataClientException
import com.yomira.core.network.NetworkConfiguration
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the detail-page connections pipeline end to end: a shelf card with a
 * non-AniList id must be resolved through the AniList title search and yield
 * its watchable adaptations (v8.2.1 debt: adaptation badges missing on device).
 */
class SeriesConnectionRepositoryTest {

    private fun server(): MockWebServer = MockWebServer()

    private fun repository(server: MockWebServer): SeriesConnectionRepository {
        val client = AniListClient(
            OkHttpClient(),
            config = NetworkConfiguration(aniListEndpoint = server.url("/graphql").toString()),
        )
        return SeriesConnectionRepository(client)
    }

    /** A shelf card exactly like the QA library: MangaDex id, English display title. */
    private fun shelfCard(title: String = "One Piece", vararg altTitles: String) = Manga(
        id = "mangadex:some-uuid",
        title = title,
        altTitles = altTitles.toList(),
        sourceId = "mangadex",
    )
    @Test
    fun `shelf card resolves through title search and yields adaptations`() = runTest {
        server().use { server ->
            // 1st request: the title search.
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"data":{"page":{"media":[
                      {"id":30013,"title":{"romaji":"One Piece"},"staff":{"edges":[]}},
                      {"id":104563,"title":{"romaji":"One Piece: Roronoa Zoro Falls Into the Sea"},"staff":{"edges":[]}}
                    ]}}}
                    """.trimIndent(),
                ),
            )
            // 2nd request: the relations graph of the matched record.
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"data":{"media":{"id":30013,"relations":{"edges":[
                      {"relationType":"ADAPTATION","node":{"id":21,"type":"ANIME","format":"TV","episodes":1122,"startDate":{"year":1999},"averageScore":89,"title":{"romaji":"One Piece"}}},
                      {"relationType":"SEQUEL","node":{"id":30013,"type":"MANGA","title":{"romaji":"One Piece: Chapter 1044"}}}
                    ]}}}}
                    """.trimIndent(),
                ),
            )
            val repository = repository(server)

            val connections = repository.connectionsFor(shelfCard())

            assertEquals(listOf("anilist:21"), connections.adaptations.map { it.id })
            assertEquals(AdaptationKind.ANIME, connections.adaptations.single().kind)
            assertEquals(listOf("anilist:30013"), connections.related.map { it.id })
            // The search must have used the display title of the shelf card.
            val search = server.takeRequest().body.readUtf8()
            assertTrue(search.contains("One Piece"))
        }
    }

    @Test
    fun `alt titles are tried when the display title has no AniList match`() = runTest {
        server().use { server ->
            // Display title search: no usable match.
            server.enqueue(
                MockResponse().setBody("""{"data":{"page":{"media":[]}}}"""),
            )
            // Alt title search: the romaji match.
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"data":{"page":{"media":[
                      {"id":154587,"title":{"romaji":"Sousou no Frieren"},"staff":{"edges":[]}}
                    ]}}}
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"data":{"media":{"id":154587,"relations":{"edges":[
                      {"relationType":"ADAPTATION","node":{"id":154587,"type":"ANIME","format":"TV","title":{"romaji":"Sousou no Frieren"}}}
                    ]}}}}
                    """.trimIndent(),
                ),
            )
            val repository = repository(server)

            val connections = repository.connectionsFor(
                shelfCard(title = "Frieren", altTitles = arrayOf("Sousou no Frieren")),
            )

            assertEquals(listOf("anilist:154587"), connections.adaptations.map { it.id })
        }
    }

    @Test
    fun `unreachable AniList propagates the failure to the progressive-enhancement caller`() = runTest {
        server().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val repository = repository(server)

            try {
                repository.connectionsFor(shelfCard())
                throw AssertionError("Expected MetadataClientException")
            } catch (expected: MetadataClientException) {
                assertEquals("anilist", expected.sourceId)
            }
        }
    }

    @Test
    fun `series unknown to AniList yields empty showcases without failing`() = runTest {
        server().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"page":{"media":[]}}}"""))
            val repository = repository(server)

            val connections = repository.connectionsFor(shelfCard(title = "Unknown Series"))

            assertTrue(connections.adaptations.isEmpty())
            assertTrue(connections.related.isEmpty())
            assertTrue(connections.recommended.isEmpty())
        }
    }
}
