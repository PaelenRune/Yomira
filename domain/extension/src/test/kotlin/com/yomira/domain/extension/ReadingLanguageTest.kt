package com.yomira.domain.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingLanguageTest {
    @Test fun `single language accepts regional and multilingual sources`() {
        assertTrue(ReadingLanguage.TURKISH.accepts(" tr-TR "))
        assertTrue(ReadingLanguage.ENGLISH.accepts("en_US"))
        assertTrue(ReadingLanguage.TURKISH.accepts("all"))
        assertFalse(ReadingLanguage.TURKISH.accepts("en"))
    }

    @Test fun `both is limited to Turkish English and multilingual sources`() {
        listOf("tr", "en", "EN-us", "tr_TR", "all", "multi", "mul").forEach {
            assertTrue(it, ReadingLanguage.BOTH.accepts(it))
        }
        listOf("ja", "ko", "fr", "", "both").forEach {
            assertFalse(it, ReadingLanguage.BOTH.accepts(it))
        }
    }
}
