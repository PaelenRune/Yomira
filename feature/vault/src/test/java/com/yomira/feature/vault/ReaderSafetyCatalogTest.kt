package com.yomira.feature.vault

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSafetyCatalogTest {
    @Test fun `unlocked vault still respects blacklist and age`() {
        val allowed = Manga("allowed", "Allowed", genres = listOf("Manga"), contentRating = ContentRating.EXPLICIT, sourceId = "test")
        val excluded = allowed.copy(id = "excluded", genres = listOf("Manga", "Netorare"))
        val items = listOf(allowed, excluded)
        assertEquals(listOf(allowed), vaultCatalog(items, true, excludedTags = setOf("ntr")))
        assertTrue(vaultCatalog(items, true, isAdult = false).isEmpty())
        assertTrue(vaultCatalog(items, true, allowAdultContent = false).isEmpty())
        assertTrue(vaultCatalog(items, false).isEmpty())
    }
}
