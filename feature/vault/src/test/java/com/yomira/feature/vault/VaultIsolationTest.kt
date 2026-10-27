package com.yomira.feature.vault

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.*
import org.junit.Test

class VaultIsolationTest {
    private val series = ContentRating.entries.map { Manga(it.name, it.name, sourceId = "source", contentRating = it, genres = listOf("Hentai")) }
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
            "Hentai Manga" to listOf("Romance, Drama, HENTAI", "Hentai-Manga", "Japanese Hentai Manga"),
            "Smut" to listOf("Romance | SMUT", "Adult Smut Manga", "Erotica"),
            "Doujinshi" to listOf("Drama; DOUJINSHI", "Doujin", "Hentai Doujinshi"),
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
        listOf("Adult Webtoons", "Hentai Manga", "Smut", "Doujinshi").forEach { chip ->
            assertTrue(vaultCatalog(items, true, chip).isEmpty())
        }
        assertEquals(items, vaultCatalog(items, true))
    }

    @Test fun `mature milf party never enters vault even when genre says hentai`() {
        val item = Manga("milf", "I Formed a MILF Party with the Heroes' Mothers", sourceId = "source",
            contentRating = ContentRating.MATURE, genres = listOf("Hentai", "Smut", "Manhwa", "Doujinshi"))
        listOf(null, "Adult Webtoons", "Hentai Manga", "Smut", "Doujinshi").forEach { chip ->
            assertTrue(vaultCatalog(listOf(item), true, chip).isEmpty())
        }
    }

    @Test fun `clicking an active chip clears the genre and restores all explicit records`() {
        val selected = toggleVaultGenre(null, "Hentai Manga")
        assertEquals("Hentai Manga", selected)
        assertNull(toggleVaultGenre(selected, "Hentai Manga"))
        assertEquals("Smut", toggleVaultGenre(selected, "Smut"))
        assertEquals(vaultCatalog(series, true), vaultCatalog(series, true, toggleVaultGenre(selected, "Hentai Manga")))
    }
}
