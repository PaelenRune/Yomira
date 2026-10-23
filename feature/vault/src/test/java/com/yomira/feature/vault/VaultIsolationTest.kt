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
}
