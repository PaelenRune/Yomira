package com.yomira.data.metadata

import org.junit.Assert.*
import org.junit.Test

class TitleLocalizerTest {
    @Test fun providerOrderDoesNotChangeSelection() {
        val first = com.yomira.core.network.RemoteMetadata("a", "1", "Owner",
            englishTitle = "Zulu", altTitles = mapOf("ja" to listOf("日本語")))
        val second = first.copy(sourceId = "b", englishTitle = "Alpha")
        assertEquals("Alpha", TitleLocalizer.select(listOf(first, second)))
        assertEquals("Alpha", TitleLocalizer.select(listOf(second, first)))
        assertEquals("日本語", TitleLocalizer.select(listOf(first.copy(englishTitle = null))))
    }
    @Test fun englishWins() = assertEquals("English", TitleLocalizer.select(listOf(" English "), mapOf("ja" to listOf("日本語")), listOf("First owner")))
    @Test fun japaneseBeatsOwnerWithoutEnglish() = assertEquals("日本語", TitleLocalizer.select(emptyList(), mapOf("ja" to listOf("日本語")), listOf("First owner")))
    @Test fun hangulSurvives() = assertEquals("나 혼자", TitleLocalizer.select(emptyList(), mapOf("ko-KR" to listOf("나 혼자")), listOf("Autre")))
    @Test fun romajiIsOriginalNotEnglish() = assertEquals("Original", TitleLocalizer.select(emptyList(), mapOf("en_jp" to listOf("Original")), listOf("Autre")))
    @Test fun blankNamesAreIgnored() = assertNull(TitleLocalizer.select(listOf(" ", null), mapOf("en" to listOf("")), listOf(" ")))
    @Test fun englishVariantsBeatOriginals() {
        for (code in listOf("en", "en-US", "EN_us", " en-GB ")) {
            assertEquals("English", TitleLocalizer.select(emptyList(), mapOf("ja" to listOf("日本語"), code to listOf("English")), listOf("Owner")))
        }
    }
    @Test fun otherTitleIsLastResort() = assertEquals("Other", TitleLocalizer.select(emptyList(), emptyMap(), listOf("", "Other")))
}
