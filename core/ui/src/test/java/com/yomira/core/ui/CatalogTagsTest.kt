package com.yomira.core.ui

import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTagsTest {
    private fun manga(id: String, genres: List<String>) = Manga(id, id, genres = genres, sourceId = "seed")

    @Test fun `matrix carries at least the promised thirty tags`() {
        assertTrue("tag matrix must expose 30+ tags", CatalogTags.all.size >= 30)
        assertEquals(CatalogTags.all.size, CatalogTags.all.map { it.id }.distinct().size)
        assertTrue(CatalogTags.all.all { tag -> tag.aliases.isNotEmpty() && tag.aliases.all { it.isNotBlank() } })
    }

    @Test fun `ecchi comedy selection combines as AND`() {
        val shelf = listOf(
            manga("ecchi-comedy", listOf("Ecchi", "Comedy", "Fanservice")),
            manga("ecchi-only", listOf("Ecchi")),
            manga("comedy-only", listOf("Comedy")),
        )
        val and = filterByTags(shelf, listOf("ecchi", "comedy"), matchAll = true)
        val or = filterByTags(shelf, listOf("ecchi", "comedy"), matchAll = false)
        assertEquals(listOf("ecchi-comedy"), and.map { it.id })
        assertEquals(listOf("ecchi-comedy", "ecchi-only", "comedy-only"), or.map { it.id })
    }

    @Test fun `turkish and english spellings map onto the same tag`() {
        val tag = requireNotNull(CatalogTags.byId["action"])
        assertTrue(tag.matchesGenre("Action"))
        assertTrue(tag.matchesGenre("Aksiyon"))
        assertTrue(tag.matchesGenre("action"))
        val scifi = requireNotNull(CatalogTags.byId["sci-fi"])
        assertTrue(scifi.matchesGenre("Sci-Fi"))
        assertTrue(scifi.matchesGenre("Bilim Kurgu"))
    }

    @Test fun `word matching keeps unrelated genres apart`() {
        val adultWebtoons = requireNotNull(CatalogTags.byId["webtoon"])
        assertTrue(adultWebtoons.matchesGenre("Adult Webtoons"))
        assertTrue(adultWebtoons.matchesGenre("Manhwa"))
        val action = requireNotNull(CatalogTags.byId["action"])
        // Compound provider genres such as "Action & Adventure" must still hit the word "action"…
        assertTrue(action.matchesGenre("Action & Adventure"))
        // …while a mere substring collision like "Reactions" must not.
        assertTrue(!action.matchesGenre("Reactions"))
        assertTrue(!action.matchesGenre("Romance"))
    }

    @Test fun `empty selection is a no-op and unknown ids are ignored`() {
        val shelf = listOf(manga("a", listOf("Action")))
        assertEquals(shelf, filterByTags(shelf, emptyList()))
        assertEquals(shelf, filterByTags(shelf, listOf("does-not-exist")))
        assertEquals(emptyList<Manga>(), filterByTags(shelf, listOf("comedy")))
    }

    @Test fun `isekai aliases cover the common provider spellings`() {
        val isekai = requireNotNull(CatalogTags.byId["isekai"])
        listOf("Isekai", "Reincarnation", "Transported to Another World").forEach {
            assertTrue("$it should map to isekai", isekai.matchesGenre(it))
        }
    }
}
