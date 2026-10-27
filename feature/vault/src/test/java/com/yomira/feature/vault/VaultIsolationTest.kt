package com.yomira.feature.vault

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.*
import org.junit.Test

class VaultIsolationTest {
    private val series = ContentRating.entries.map { Manga(it.name, it.name, sourceId = "source", contentRating = it, genres = listOf("Hentai Manga")) }
    @Test fun `locked vault exposes no records`() { assertTrue(vaultCatalog(series, false).isEmpty()) }
    @Test fun `unlocked vault contains explicit records only`() {
        assertEquals(listOf(ContentRating.EXPLICIT), vaultCatalog(series, true).map { it.contentRating })
    }
    @Test fun `filter aliases cannot bypass rating gate`() {
        assertEquals(1, vaultCatalog(series, true, "Hentai Manga").size)
        assertTrue(vaultCatalog(series, true, "Smut").isEmpty())
        assertTrue(vaultCatalog(series, false, "Hentai Manga").isEmpty())
    }

    @Test fun `chips match delimited mixed case labels and format aliases`() {
        val labels = mapOf(
            "Adult Webtoons" to listOf(" Romance, Adult Webtoon ", " Full-Color MANHWA ", "Pornhwa", "Webtoons"),
            "Hentai Manga" to listOf("Romance, Drama, HENTAI Manga", "Hentai-Manga", "Japanese Hentai Manga"),
            "Smut" to listOf("Romance | SMUT", "Adult Smut Manga", "Erotica"),
            "Doujinshi" to listOf("Drama; DOUJINSHI", "Doujin", "Hentai Doujinshi"),
            "Western / 3D" to listOf("Western Erotica", "Erotic Comics", "3D Porn", "3D Comics", "AllPornComic", "Romance | ALLPORNCOMIC"),
        )
        labels.forEach { (chip, values) ->
            values.forEach { value ->
                val item = Manga(value, value, sourceId = "source", contentRating = ContentRating.EXPLICIT, genres = listOf(value))
                assertEquals("$chip should match $value", listOf(item), vaultCatalog(listOf(item), true, " ${chip.uppercase()} "))
            }
        }
    }

    @Test fun `missing genres and substring lookalikes do not match chips`() {
        val items = listOf(emptyList(), listOf("Nonhentai", "Nonsmut", "Notdoujinshi", "Manhwatastic")).mapIndexed { index, genres ->
            Manga("$index", "Unknown", sourceId = "source", contentRating = ContentRating.EXPLICIT, genres = genres)
        }
        listOf("Adult Webtoons", "Hentai Manga", "Smut", "Doujinshi", "Western / 3D").forEach { chip ->
            assertTrue(vaultCatalog(items, true, chip).isEmpty())
        }
        assertEquals(items, vaultCatalog(items, true))
    }

    @Test fun `mature milf party never enters vault even when genre says hentai`() {
        val item = Manga("milf", "I Formed a MILF Party with the Heroes' Mothers", sourceId = "source",
            contentRating = ContentRating.MATURE, genres = listOf("Hentai", "Smut", "Manhwa", "Doujinshi"))
        listOf(null, "Adult Webtoons", "Hentai Manga", "Smut", "Doujinshi", "Western / 3D").forEach { chip ->
            assertTrue(vaultCatalog(listOf(item), true, chip).isEmpty())
        }
    }

    @Test fun `western filter recognizes source identity but never bypasses rating or lock`() {
        val items = ContentRating.entries.map { rating ->
            Manga(rating.name, "Unknown", sourceId = "en.allporncomic", contentRating = rating)
        }
        assertEquals(listOf(items.single { it.contentRating == ContentRating.EXPLICIT }),
            vaultCatalog(items, true, "Western / 3D"))
        assertTrue(vaultCatalog(items, false, "Western / 3D").isEmpty())
        val lookalike = items.last().copy(sourceId = "notallporncomic", contentRating = ContentRating.EXPLICIT)
        assertTrue(vaultCatalog(listOf(lookalike), true, "Western / 3D").isEmpty())
    }

    @Test fun `clicking an active chip clears the genre and restores all explicit records`() {
        val selected = toggleVaultGenre(null, "Hentai Manga")
        assertEquals("Hentai Manga", selected)
        assertNull(toggleVaultGenre(selected, "Hentai Manga"))
        assertEquals("Smut", toggleVaultGenre(selected, "Smut"))
        assertEquals(vaultCatalog(series, true), vaultCatalog(series, true, toggleVaultGenre(selected, "Hentai Manga")))
    }
}
