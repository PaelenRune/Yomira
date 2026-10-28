package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSafetyCatalogTest {
    @Test fun `public catalog respects the tier matrix and the blacklist`() {
        val safe = Manga("safe", "Safe", sourceId = "test")
        val excluded = safe.copy(id = "excluded", genres = listOf("Boys' Love"))
        val mature = safe.copy(id = "mature", contentRating = ContentRating.MATURE)
        val explicit = safe.copy(id = "explicit", contentRating = ContentRating.EXPLICIT)
        val items = listOf(safe, excluded, mature, explicit)

        // v8.2.0: safe mode (under 18) sees SAFE only; the blacklist still applies.
        assertEquals(listOf(safe), publicCatalog(items, setOf("yaoi"), isAdult = false))
        assertEquals(listOf(safe, excluded), publicCatalog(items, isAdult = false))

        // v8.2.0: 18+ additionally sees MATURE, never EXPLICIT.
        assertEquals(listOf(safe, mature), publicCatalog(items, setOf("yaoi"), isAdult = true))
        assertEquals(listOf(safe, excluded, mature), publicCatalog(items, isAdult = true))
    }
}
