package com.yomira.app.catalog

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import org.junit.Assert.*
import org.junit.Test

class ReadingSourcePolicyTest {
    private val frieren = Manga("frieren", "Frieren", sourceId = "metadata")
    private val allowed = ReaderPreferences(ReadingLanguage.ENGLISH, allowAdultContent = true, isAdult = true)

    @Test fun `adult consent is preserved for safe titles even with vault locked`() {
        assertEquals(allowed, readingSourcePreferences(frieren, allowed, unlocked = false))
    }

    @Test fun `safe mode and missing age consent never enable adult sources`() {
        assertFalse(readingSourcePreferences(frieren, allowed.copy(allowAdultContent = false), false)!!.allowAdultContent)
        assertFalse(readingSourcePreferences(frieren, allowed.copy(isAdult = false), true)!!.allowAdultContent)
    }

    @Test fun `blacklist wins for every rating even with adult consent and vault unlocked`() {
        ContentRating.entries.forEach { rating ->
            assertNull(readingSourcePreferences(frieren.copy(genres = listOf("Futanari"), contentRating = rating),
                allowed.copy(excludedTags = setOf("futanari")), unlocked = true))
        }
    }

    @Test fun `explicit titles still require both consent and vault unlock`() {
        val explicit = frieren.copy(contentRating = ContentRating.EXPLICIT)
        assertNull(readingSourcePreferences(explicit, allowed, unlocked = false))
        assertNull(readingSourcePreferences(explicit, allowed.copy(allowAdultContent = false), unlocked = true))
        assertEquals(allowed, readingSourcePreferences(explicit, allowed, unlocked = true))
    }
}
