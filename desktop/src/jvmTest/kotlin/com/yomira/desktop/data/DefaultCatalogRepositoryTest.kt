package com.yomira.desktop.data

import com.yomira.core.network.MetadataProvider
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.RemoteMetadata
import com.yomira.desktop.state.PageCursor
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search-side behaviour of the desktop waterfall: parallel fan-out, title
 * de-duplication, and the adult-content boundary. Browse/detail need the real
 * HTTP clients, so they are covered by the manual validation run instead.
 */
class DefaultCatalogRepositoryTest {

    private fun provider(
        id: String,
        records: List<RemoteMetadata>,
        failure: Throwable? = null,
    ) = object : MetadataProvider {
        override val sourceId = id
        override suspend fun search(query: String, limit: Int): List<RemoteMetadata> {
            failure?.let { throw it }
            return records
        }
    }

    private fun repository(vararg providers: MetadataProvider) = DefaultCatalogRepository(
        httpClient = OkHttpClient(),
        providers = providers.toList(),
    )

    @Test
    fun `all providers are searched in parallel and results dedupe by alias`() = runTest {
        val baka = provider(
            MetadataSources.BAKA_UPDATES,
            listOf(
                RemoteMetadata(
                    MetadataSources.BAKA_UPDATES,
                    "1",
                    "Shingeki no Kyojin",
                    altTitles = mapOf("en" to listOf("Attack on Titan")),
                    description = "Titans",
                ),
            ),
        )
        val aniList = provider(
            MetadataSources.ANILIST,
            listOf(
                RemoteMetadata(
                    MetadataSources.ANILIST,
                    "2",
                    "Attack on Titan",
                    englishTitle = "Attack on Titan",
                    coverUrl = "https://img.example/aot.jpg",
                ),
                RemoteMetadata(MetadataSources.ANILIST, "3", "Berserk"),
            ),
        )
        val repo = repository(baka, aniList)

        val results = repo.search("attack on titan")

        assertEquals(2, results.size)
        val aot = results.first()
        assertEquals("Attack on Titan", aot.title)
        assertEquals("Titans", aot.synopsis)
        assertEquals("https://img.example/aot.jpg", aot.coverUrl)
        assertEquals("Berserk", results.last().title)
    }

    @Test
    fun `a failing provider never cancels the search`() = runTest {
        val repo = repository(
            provider(MetadataSources.BAKA_UPDATES, emptyList(), failure = IllegalStateException("down")),
            provider(MetadataSources.ANILIST, listOf(RemoteMetadata(MetadataSources.ANILIST, "7", "Berserk"))),
        )
        val results = repo.search("berserk")
        assertEquals(1, results.size)
        assertEquals("Berserk", results.single().title)
    }

    @Test
    fun `explicit records are filtered out before rendering`() = runTest {
        val repo = repository(
            provider(
                MetadataSources.MANGADEX,
                listOf(
                    RemoteMetadata(MetadataSources.MANGADEX, "safe", "One Piece", genres = listOf("Action")),
                    RemoteMetadata(
                        MetadataSources.MANGADEX,
                        "explicit",
                        "Adult Thing",
                        tags = listOf("Hentai"),
                    ),
                    RemoteMetadata(
                        MetadataSources.MANGADEX,
                        "porn",
                        "Another",
                        contentRating = com.yomira.core.model.ExternalRating.PORNOGRAPHIC,
                    ),
                ),
            ),
        )
        val results = repo.search("thing")
        assertEquals(listOf("One Piece"), results.map { it.title })
    }

    @Test
    fun `a blank query never touches the network`() = runTest {
        val repo = repository(provider(MetadataSources.ANILIST, listOf(RemoteMetadata(MetadataSources.ANILIST, "1", "X"))))
        assertTrue(repo.search("   ").isEmpty())
    }

    @Test
    fun `browse rejects an out of range page size`() = runTest {
        val repo = repository()
        try {
            repo.browse(PageCursor.First, pageSize = 0)
            throw AssertionError("page size 0 must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
        try {
            repo.browse(PageCursor.First, pageSize = CatalogRepository.MAX_PAGE_SIZE + 1)
            throw AssertionError("oversized pages must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
        assertFalse(PageCursor.First.hasPrevious)
    }
}
