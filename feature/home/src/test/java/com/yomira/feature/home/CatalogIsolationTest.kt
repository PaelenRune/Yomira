package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogIsolationTest {
    @Test fun `public catalog never exposes explicit records`() {
        val records = ContentRating.entries.map { Manga(it.name, it.name, contentRating = it, sourceId = "source") }
        assertEquals(listOf(ContentRating.SAFE, ContentRating.MATURE), publicCatalog(records).map { it.contentRating })
    }
    @Test fun `explicit only search input produces empty public result`() {
        assertEquals(emptyList<Manga>(), publicCatalog(listOf(Manga("adult", "Adult", contentRating = ContentRating.EXPLICIT, sourceId = "source"))))
    }
}
