package com.yomira.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleManagerCompatTest {
    @Test
    fun `missing language defaults to English`() {
        assertEquals(LocaleManagerCompat.ENGLISH, LocaleManagerCompat.supportedTag(null))
        assertEquals(LocaleManagerCompat.ENGLISH, LocaleManagerCompat.supportedTag(""))
    }

    @Test
    fun `Turkish reading language selects Turkish UI`() {
        listOf("tr", "tr-TR", "TR").forEach { language ->
            assertEquals(LocaleManagerCompat.TURKISH, LocaleManagerCompat.supportedTag(language))
        }
    }

    @Test
    fun `English both and unsupported languages select English UI`() {
        listOf("en", "en-US", "both", "de").forEach { language ->
            assertEquals(LocaleManagerCompat.ENGLISH, LocaleManagerCompat.supportedTag(language))
        }
    }
}
