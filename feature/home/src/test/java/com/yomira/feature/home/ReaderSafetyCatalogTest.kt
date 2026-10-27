package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSafetyCatalogTest {
    @Test fun `public catalog enforces age and blacklist before display`() {
        val safe = Manga("safe", "Safe", sourceId = "test")
        val excluded = safe.copy(id = "excluded", genres = listOf("Boys' Love"))
        val mature = safe.copy(id = "mature", contentRating = ContentRating.MATURE)
        val explicit = safe.copy(id = "explicit", contentRating = ContentRating.EXPLICIT)
        val items = listOf(safe, excluded, mature, explicit)
        assertEquals(listOf(safe), publicCatalog(items, setOf("yaoi"), isAdult = false))
        assertEquals(listOf(safe, mature), publicCatalog(items, setOf("yaoi"), isAdult = true))
    }
}
