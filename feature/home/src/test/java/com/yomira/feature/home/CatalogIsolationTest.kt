package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogIsolationTest {
    @Test fun `public catalog never exposes plus18 records`() {
        val records = ContentRating.entries.map { Manga(it.name, it.name, contentRating = it, sourceId = "source") }
        // v8.1.4: the public grid keeps SAFE only; MATURE lives behind the Vault,
        // and EXPLICIT was already excluded.
        assertEquals(listOf(ContentRating.SAFE), publicCatalog(records).map { it.contentRating })
    }
    @Test fun `explicit only search input produces empty public result`() {
        assertEquals(emptyList<Manga>(), publicCatalog(listOf(Manga("adult", "Adult", contentRating = ContentRating.EXPLICIT, sourceId = "source"))))
    }
}
