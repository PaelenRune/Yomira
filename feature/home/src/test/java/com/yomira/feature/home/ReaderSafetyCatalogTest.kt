package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSafetyCatalogTest {
    @Test fun `public catalog is strictly 18-free and respects the blacklist`() {
        val safe = Manga("safe", "Safe", sourceId = "test")
        val excluded = safe.copy(id = "excluded", genres = listOf("Boys' Love"))
        val mature = safe.copy(id = "mature", contentRating = ContentRating.MATURE)
        val explicit = safe.copy(id = "explicit", contentRating = ContentRating.EXPLICIT)
        val items = listOf(safe, excluded, mature, explicit)
        // v8.1.4: +18 never reaches a public surface, regardless of the reader's age.
        assertEquals(listOf(safe), publicCatalog(items, setOf("yaoi")))
        // v8.1.4: without a blacklist a SAFE record stays in the public grid.
        assertEquals(listOf(safe, excluded), publicCatalog(items))
    }
}
