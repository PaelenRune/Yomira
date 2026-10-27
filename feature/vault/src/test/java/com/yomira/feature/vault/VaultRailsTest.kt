package com.yomira.feature.vault

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.*
import org.junit.Test

class VaultRailsTest {
    private fun manga(id: String, genre: String, score: Double = 7.0, rating: ContentRating = ContentRating.EXPLICIT) =
        Manga(id, id, genres = listOf(genre), score = score, contentRating = rating, sourceId = "source")

    private val catalog = listOf(
        manga("webtoon", "Manhwa"),
        manga("hentai", "Hentai", 9.0),
        manga("doujin", "Doujinshi"),
        manga("western", "3D Comics"),
        manga("unclassified", "Romance"),
    )

    @Test fun `all styles have dedicated rails while unclassified imports remain in collection`() {
        val home = buildVaultHome(catalog, emptyList(), true)
        assertEquals(vaultGenreChips, home.rails.map { it.style })
        assertEquals(listOf("webtoon", "hentai", "doujin", "western"), home.rails.flatMap { it.items }.map { it.id })
        assertEquals(catalog, home.collection)
        assertEquals("hentai", home.hero.first().id)
    }

    @Test fun `selected style scopes hero rails and collection together`() {
        vaultGenreChips.forEach { style ->
            val home = buildVaultHome(catalog, emptyList(), true, style)
            val expected = vaultCatalog(catalog, true, style)
            assertEquals(listOf(style), home.rails.map { it.style })
            assertEquals(expected, home.collection)
            assertEquals(expected, home.hero)
            assertEquals(expected, home.rails.single().items)
        }
    }

    @Test fun `every surface rejects non explicit records in catalog and discovery`() {
        val mixed = ContentRating.entries.flatMap { rating -> catalog.map { it.copy(id = "${rating}-${it.id}", contentRating = rating) } }
        (listOf<String?>(null) + vaultGenreChips).forEach { style ->
            val home = buildVaultHome(mixed, mixed, true, style)
            val surfaces = listOf(home.collection, home.hero) + home.rails.map { it.items }
            surfaces.forEach { items ->
                assertTrue(items.isNotEmpty())
                assertTrue(items.all { it.contentRating == ContentRating.EXPLICIT })
                assertEquals(items.size, items.distinctBy { it.id }.size)
            }
        }
    }

    @Test fun `locked snapshot has no hero collection or rails`() {
        (listOf<String?>(null) + vaultGenreChips).forEach { style ->
            assertEquals(VaultHome(emptyList(), emptyList(), emptyList()), buildVaultHome(catalog, catalog, false, style))
        }
    }

    @Test fun `western imports without tags are included by source identity`() {
        val imported = manga("imported", "").copy(sourceId = "en.allporncomic")
        val home = buildVaultHome(catalog, listOf(imported), true, "Western / 3D")
        assertTrue(imported in home.collection)
        assertTrue(imported in home.hero)
        assertTrue(imported in home.rails.single().items)
    }

    @Test fun `new doujinshi prioritize discovery without inventing release dates`() {
        val new = manga("new", "Doujinshi", 5.0)
        val old = manga("old", "Doujinshi", 10.0)
        val home = buildVaultHome(listOf(old), listOf(new, old.copy(score = 1.0)), true, "Doujinshi", railLimit = 1)
        // Both are discovered here, so their canonical scores break the tie.
        assertEquals(listOf(old), home.rails.single().items)
        assertEquals(listOf(new, old), buildVaultHome(listOf(old), listOf(new), true, "Doujinshi").rails.single().items)
        assertEquals(listOf(old, new), home.hero)
    }

    @Test fun `hero and rail limits never truncate the full collection`() {
        val items = (1..20).map { manga("series-$it", "Hentai") }
        val home = buildVaultHome(items, items, true)
        assertEquals(20, home.collection.size)
        assertEquals(6, home.hero.size)
        assertEquals(14, home.rails.single { it.style == "Hentai Manga" }.items.size)
    }
}
