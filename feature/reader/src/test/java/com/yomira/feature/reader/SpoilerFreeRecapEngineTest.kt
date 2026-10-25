package com.yomira.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoilerFreeRecapEngineTest {

    private fun chapter(index: Int, number: Double, vararg events: String) = RecapChapter(
        index = index,
        chapterNumber = number,
        title = "Bölüm $number",
        events = events.toList(),
        characters = if (events.isEmpty()) emptyList() else listOf("Kahraman $index"),
    )

    @Test
    fun `recap stops at the last read chapter`() {
        val chapters = listOf(
            chapter(0, 1.0, "Kahraman köye ulaştı"),
            chapter(1, 2.0, "Kılıç bulundu"),
            chapter(2, 3.0).copy(title = "Ejder uyandı"), // catalogue metadata only
            chapter(3, 4.0).copy(title = "Kahraman öldü"), // must never leak
        )
        val recap = SpoilerFreeRecapEngine.build(chapters, lastReadChapterIndex = 1)
        assertEquals(2, recap.coveredChapters)
        assertEquals(2.0, recap.upToChapterNumber, 1e-6)
        assertTrue(recap.beats.none { it.text.contains("Ejder") })
        assertTrue(recap.beats.none { it.text.contains("öldü") })
    }

    @Test
    fun `guard rejects any beat past the cutoff`() {
        val chapters = listOf(chapter(0, 1.0, "A"), chapter(1, 2.0, "B"))
        val recap = SpoilerFreeRecapEngine.build(chapters, lastReadChapterIndex = 1)
        recap.verifySpoilerFree(1) // passes
        try {
            recap.verifySpoilerFree(0)
            throw AssertionError("Expected the spoiler guard to throw")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `build rejects a source offering events from unread chapters`() {
        val chapters = listOf(chapter(0, 1.0, "A"), chapter(1, 2.0, "Secret"))
        try {
            SpoilerFreeRecapEngine.build(chapters, lastReadChapterIndex = 0)
            throw AssertionError("Expected the build to reject future events")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `long histories are condensed but stay ordered`() {
        val chapters = (0..19).map { chapter(it, it + 1.0, "Olay $it") }
        val recap = SpoilerFreeRecapEngine.build(chapters, lastReadChapterIndex = 19)
        assertEquals(20, recap.coveredChapters)
        assertTrue(recap.condensed)
        assertTrue(recap.beats.size <= 12)
        // The most recent events survive the condensation.
        assertTrue(recap.beats.any { it.text.contains("Olay 19") })
    }

    @Test
    fun `characters are collected only from covered chapters`() {
        val chapters = listOf(
            chapter(0, 1.0, "A").copy(characters = listOf("Ada", "Bora")),
            chapter(1, 2.0, "B").copy(characters = listOf("ada", "Cem")),
            chapter(2, 3.0).copy(title = "Gelecek Karakter"), // unread, metadata only
        )
        val recap = SpoilerFreeRecapEngine.build(chapters, lastReadChapterIndex = 1)
        // Case-insensitive dedupe keeps the first spelling; unread cast stays out.
        assertEquals(listOf("Ada", "Bora", "Cem"), recap.characters)
        assertTrue(recap.beats.none { it.text.contains("Gelecek") })
    }

    @Test
    fun `empty history yields an empty recap`() {
        val recap = SpoilerFreeRecapEngine.build(emptyList(), lastReadChapterIndex = 0)
        assertEquals(0, recap.coveredChapters)
        assertTrue(recap.beats.isEmpty())
    }

    @Test
    fun `history availability gates the recap button`() {
        val chapters = listOf(chapter(0, 1.0, "A"))
        assertTrue(SpoilerFreeRecapEngine.hasHistory(chapters, 0))
        assertTrue(!SpoilerFreeRecapEngine.hasHistory(chapters, -1) || false)
    }
}
