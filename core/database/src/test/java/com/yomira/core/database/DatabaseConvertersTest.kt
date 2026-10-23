package com.yomira.core.database

import com.yomira.core.database.converter.DatabaseConverters
import com.yomira.core.model.ContentRating
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatabaseConvertersTest {
    private val converters = DatabaseConverters()

    @Test
    fun `list round trip preserves delimiters unicode quotes and empty values`() {
        val values = listOf("A,B", "Başlık", "日本語", "\"quoted\"", "", "line\nbreak", "\\")
        assertEquals(values, converters.jsonToStringList(converters.stringListToJson(values)))
        assertEquals(emptyList<String>(), converters.jsonToStringList(converters.stringListToJson(emptyList())))
    }

    @Test
    fun `dates preserve epoch milliseconds and nulls`() {
        val date = Date(1_700_000_000_123)
        assertEquals(date, converters.epochMillisToDate(converters.dateToEpochMillis(date)))
        assertNull(converters.dateToEpochMillis(null))
        assertNull(converters.epochMillisToDate(null))
    }

    @Test
    fun `enum round trips use stable values`() {
        ContentRating.entries.forEach {
            assertEquals(it, converters.levelToContentRating(converters.contentRatingToLevel(it)))
        }
        ReadingMode.entries.forEach {
            assertEquals(it, converters.stringToReadingMode(converters.readingModeToString(it)))
        }
        MangaStatus.entries.forEach {
            assertEquals(it, converters.stringToMangaStatus(converters.mangaStatusToString(it)))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid rating fails instead of losing information`() {
        converters.levelToContentRating(99)
    }
}
