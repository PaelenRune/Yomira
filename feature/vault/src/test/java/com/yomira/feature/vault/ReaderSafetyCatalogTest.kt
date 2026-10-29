package com.yomira.feature.vault

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSafetyCatalogTest {
    @Test fun `all vault surfaces always hide hard core`() {
        val items = listOf("Futa", "Incest", "Bestiality", "Non-con", "Lolicon", "Shotacon").mapIndexed { i, tag ->
            Manga("$i", "Title", genres = listOf("Manhwa", tag), contentRating = ContentRating.EXPLICIT, sourceId = "test")
        }
        listOf(emptySet(), setOf("ntr"), setOf("futanari")).forEach { preferences ->
            assertTrue(vaultCatalog(items, true, excludedTags = preferences).isEmpty())
            val home = buildVaultHome(items, items, true, excludedTags = preferences)
            assertTrue(home.collection.isEmpty() && home.hero.isEmpty() && home.rails.all { it.items.isEmpty() })
        }
    }
    @Test fun `unlocked vault still respects blacklist and age`() {
        val allowed = Manga("allowed", "Allowed", genres = listOf("Manga"), contentRating = ContentRating.EXPLICIT, sourceId = "test")
        val excluded = allowed.copy(id = "excluded", genres = listOf("Manga", "Futanari"))
        val items = listOf(allowed, excluded)
        assertEquals(listOf(allowed), vaultCatalog(items, true, excludedTags = setOf("futanari")))
        assertTrue(vaultCatalog(items, true, isAdult = false).isEmpty())
        assertTrue(vaultCatalog(items, true, allowAdultContent = false).isEmpty())
        assertTrue(vaultCatalog(items, false).isEmpty())
    }
}
