package com.yomira.feature.vault

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.*
import org.junit.Test

class VaultRailsTest {
    @org.junit.Test fun `diagnostics count each filtering stage`() {
        org.junit.Assert.assertEquals("vault input=10 nonExplicit=3 excluded=2 unmatched=4 matched=1", vaultDiagnostics(10, 7, 5, 1))
    }
    @Test fun `diagnostics break down each source and rail`() {
        val rows = listOf(
            manga("ok", "Manhwa"),
            manga("hard", "Futa"),
            manga("safe", "Manhwa", rating = ContentRating.SAFE),
            manga("other", "Doujinshi").copy(sourceId = "other"),
        )
        val diagnostics = vaultRailDiagnostics(rows, emptySet(), true, true)
        assertEquals(8, diagnostics.size)
        assertTrue(diagnostics.contains("source=source rail=Adult Webtoons vault input=3 nonExplicit=1 excluded=1 unmatched=0 matched=1"))
        assertTrue(diagnostics.contains("source=other rail=Doujinshi vault input=1 nonExplicit=0 excluded=0 unmatched=0 matched=1"))
    }
    @Test fun `style preference demotes without hiding`() {
        val penalized = manga("a", "Manhwa").copy(genres = listOf("Manhwa", "NTR"))
        val neutral = manga("b", "Manhwa")
        val home = buildVaultHome(listOf(penalized, neutral), emptyList(), true, excludedTags = setOf("ntr"))
        assertEquals(listOf(neutral, penalized), home.rails.single { it.style == "Adult Webtoons" }.items)
        assertEquals(setOf(neutral, penalized), home.collection.toSet())
    }
    private fun manga(id: String, genre: String, score: Double = 7.0, rating: ContentRating = ContentRating.EXPLICIT) =
        Manga(id, id, genres = listOf(genre), score = score, contentRating = rating, sourceId = "source")

    private val catalog = listOf(
        manga("webtoon", "Manhwa"),
        manga("hentai", "Hentai Manga", 9.0),
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

    @Test fun `mature commercial manga never enter any vault surface even with hentai scraper labels`() {
        val titles = listOf("Tsugumomo", "Reincarnated Carrier's Strategy", "Yabadere Gyaru")
            .map { manga(it, "Hentai Manga", rating = ContentRating.MATURE) }
        (listOf<String?>(null) + vaultGenreChips).forEach { style ->
            val home = buildVaultHome(titles, titles, true, style)
            assertTrue(home.collection.isEmpty())
            assertTrue(home.hero.isEmpty())
            assertTrue(home.rails.all { it.items.isEmpty() })
        }
    }

    @Test fun `western imports without tags are included by source identity`() {
        val imported = manga("imported", "").copy(sourceId = "en.allporncomic")
        val home = buildVaultHome(catalog, listOf(imported), true, "Western / 3D")
        assertTrue(imported in home.collection)
        assertTrue(imported in home.hero)
        assertTrue(imported in home.rails.single().items)
    }

    @Test fun `doujinshi freshness is a bonus not a replacement for consensus ranking`() {
        val new = manga("new", "Doujinshi", 5.0)
        val old = manga("old", "Doujinshi", 10.0)
        val home = buildVaultHome(listOf(old), listOf(new, old.copy(score = 1.0)), true, "Doujinshi", railLimit = 1)
        // Both are discovered here, so their canonical scores break the tie.
        assertEquals(listOf(old), home.rails.single().items)
        assertEquals(listOf(old, new), buildVaultHome(listOf(old), listOf(new), true, "Doujinshi").rails.single().items)
        assertEquals(listOf(old, new), home.hero)
    }

    @Test fun `globally popular webtoons dominate heroes and genre rails over niche perfect ratings`() {
        val hits = listOf("Secret Class", "Boarding Diary", "Silent War")
            .map { manga(it, "Manhwa", 8.0).copy(popularity = 500_000) }
        val niche = manga("niche", "Manhwa", 10.0).copy(popularity = 5)
        val home = buildVaultHome(hits + niche, listOf(niche), true)
        assertEquals(hits.map { it.id }.toSet(), home.hero.take(3).map { it.id }.toSet())
        assertEquals(home.hero, home.rails.single { it.style == "Adult Webtoons" }.items)
        assertEquals(home.hero, buildVaultHome((hits + niche).reversed(), listOf(niche), true).hero)
    }

    @Test fun `missing popularity is safe and absent scores use neutral seven`() {
        val missing = manga("a", "Manhwa").copy(score = null)
        val zero = manga("b", "Manhwa").copy(popularity = 0)
        val home = buildVaultHome(listOf(zero, missing), emptyList(), true)
        assertEquals(listOf(missing, zero), home.hero)
    }

    @Test fun `hero and rail limits never truncate the full collection`() {
        val items = (1..20).map { manga("series-$it", "Hentai Manga") }
        val home = buildVaultHome(items, items, true)
        assertEquals(20, home.collection.size)
        assertEquals(6, home.hero.size)
        assertEquals(14, home.rails.single { it.style == "Hentai Manga" }.items.size)
    }

    @Test fun `hentai tags never override webtoon manhwa or long strip formats`() {
        val formats = listOf("Webtoon", "Webtoons", "Manhwa", "Pornhwa", "Long Strip", "Longstrip", "Vertical", "Dikey Okuma")
        val items = formats.flatMap { format ->
            listOf(
                manga("tags-$format", "Hentai, Manga | $format"),
                manga("type-$format", "Hentai Manga").copy(publicationType = format),
                manga("conflict-$format", "Hentai | $format").copy(publicationType = "Manga"),
            )
        }
        val home = buildVaultHome(items, items, true)
        assertTrue(home.rails.single { it.style == "Hentai Manga" }.items.isEmpty())
        assertTrue(home.rails.single { it.style == "Doujinshi" }.items.isEmpty())
        assertEquals(items.toSet(), buildVaultHome(items, emptyList(), true, "Adult Webtoons", railLimit = 100).rails.single().items.toSet())
        assertTrue(buildVaultHome(items, items, true, "Hentai Manga").collection.isEmpty())
    }

    @Test fun `format rails stay disjoint despite broad adult labels`() {
        val japanese = manga("japanese", "Hentai").copy(publicationType = "Manga")
        val japaneseFormatOnly = manga("manga", "Romance").copy(publicationType = "Manga")
        val fan = manga("fan", "Hentai Doujinshi")
        val western = manga("western", "Hentai | Western Erotica")
        val cg = manga("cg", "Hentai | 3D CG")
        val imported = manga("imported", "Hentai Manga").copy(sourceId = "en.allporncomic")
        val chinese = manga("chinese", "Hentai | Manhua")
        val unknownComic = manga("generic", "Adult Comic")
        val unknown = manga("unknown", "Smut | Romance")
        val unclassifiedHentai = manga("hentai-only", "Hentai")
        val items = listOf(japanese, japaneseFormatOnly, fan, western, cg, imported, chinese, unknownComic, unknown, unclassifiedHentai)
        val home = buildVaultHome(items, emptyList(), true)
        assertEquals(listOf(japanese, japaneseFormatOnly), home.rails.single { it.style == "Hentai Manga" }.items)
        assertEquals(listOf(fan), home.rails.single { it.style == "Doujinshi" }.items)
        assertEquals(setOf(western, cg, imported, unknownComic), home.rails.single { it.style == "Western / 3D" }.items.toSet())
        assertTrue(home.rails.single { it.style == "Adult Webtoons" }.items.isEmpty())
        val classifiedIds = home.rails.flatMap { it.items }.map { it.id }
        assertEquals(classifiedIds.size, classifiedIds.distinct().size)
    }
}
