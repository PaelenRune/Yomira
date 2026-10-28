package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogIsolationTest {
    @Test fun `safe mode keeps the public catalog SAFE only`() {
        val records = ContentRating.entries.map { Manga(it.name, it.name, contentRating = it, sourceId = "source") }
        // v8.2.0 placement matrix: an under-18 reader sees SAFE records only.
        assertEquals(listOf(ContentRating.SAFE), publicCatalog(records, isAdult = false).map { it.contentRating })
    }

    @Test fun `adults see SAFE and MATURE on the public surface`() {
        val records = ContentRating.entries.map { Manga(it.name, it.name, contentRating = it, sourceId = "source") }
        // v8.2.0 placement matrix: 18+ adds the MATURE (16+ themed) tier to
        // Home, Discover and search.
        assertEquals(listOf(ContentRating.SAFE, ContentRating.MATURE), publicCatalog(records, isAdult = true).map { it.contentRating })
    }

    @Test fun `explicit never reaches the public surface regardless of age or consent`() {
        // The v8.1.4 public-surface guarantee is preserved: EXPLICIT lives only
        // behind the +18 Vault lock, never on Home/Discover/search.
        listOf(true, false).forEach { isAdult ->
            assertEquals(
                emptyList<Manga>(),
                publicCatalog(listOf(Manga("adult", "Adult", contentRating = ContentRating.EXPLICIT, sourceId = "source")), isAdult = isAdult),
            )
        }
    }

    @Test fun `explicit only search input produces empty public result`() {
        assertEquals(emptyList<Manga>(), publicCatalog(listOf(Manga("adult", "Adult", contentRating = ContentRating.EXPLICIT, sourceId = "source"))))
    }
}
