package com.yomira.domain.journey.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spoiler contract of "Geçmiş Olayları Hatırla": everything the recap
 * emits must come from a chapter at or before the user's horizon, no matter
 * what the future chapters contain.
 */
class SpoilerFreeRecapEngineTest {

    private fun chapter(number: Double, title: String? = null, beats: List<String> = emptyList(), characters: List<String> = emptyList()) =
        RecapChapter(number, title, beats, characters)

    private val saga = listOf(
        chapter(1.0, "Başlangıç", listOf("Deniz kıyısında uyandı"), listOf("Arin")),
        chapter(2.0, "Yolculuk", listOf("Kuzey kapılarından geçti"), listOf("Arin", "Mira")),
        chapter(3.0, "Karanlık", listOf("Kule yıkıldı"), listOf("Karanlık Lord")),
        chapter(4.0, "Sır", listOf("Mühür kırıldı"), listOf("Karanlık Lord", "Veya")),
    )

    @Test
    fun `only chapters up to the horizon are summarized`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(seriesTitle = "Yomira Destanı", readThroughChapter = 2.0, chapters = saga),
        )
        assertEquals(2, recap.storyBeats.size)
        assertTrue(recap.storyBeats[0].contains("Deniz kıyısında uyandı"))
        assertTrue(recap.storyBeats[1].contains("Kuzey kapılarından geçti"))
        assertEquals(2, recap.withheldChapters)
    }

    @Test
    fun `the horizon chapter itself is included`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(seriesTitle = "Yomira Destanı", readThroughChapter = 3.0, chapters = saga),
        )
        assertEquals(3, recap.storyBeats.size)
        assertEquals(1, recap.withheldChapters)
    }

    @Test
    fun `characters and titles from future chapters never leak`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(seriesTitle = "Yomira Destanı", readThroughChapter = 2.0, chapters = saga),
        )
        val everything = (recap.storyBeats + recap.characters).joinToString(" ")
        assertFalse(everything.contains("Karanlık Lord"))
        assertFalse(everything.contains("Veya"))
        assertFalse(everything.contains("Kule yıkıldı"))
        assertFalse(everything.contains("Mühür kırıldı"))
        // Even the title of an unread chapter is withheld.
        assertFalse(everything.contains("Karanlık"))
        assertFalse(everything.contains("Sır"))
    }

    @Test
    fun `every horizon level is free of later content`() {
        for (horizon in -1..4) {
            val recap = SpoilerFreeRecapEngine.build(
                RecapRequest(seriesTitle = "Yomira Destanı", readThroughChapter = horizon.toDouble(), chapters = saga),
            )
            val everything = (recap.storyBeats + recap.characters).joinToString(" ")
            // A name the user already met before the horizon is not a leak.
            val knownNames = saga.filter { it.chapterNumber <= horizon }.flatMap { it.characters }.toSet()
            for (future in saga.filter { it.chapterNumber > horizon }) {
                future.beats.forEach { assertFalse("horizon $horizon leaked: $it", everything.contains(it)) }
                future.characters.filterNot { it in knownNames }
                    .forEach { assertFalse("horizon $horizon leaked: $it", everything.contains(it)) }
                future.title?.let { assertFalse("horizon $horizon leaked: $it", everything.contains(it)) }
            }
        }
    }

    @Test
    fun `nothing read yet yields an empty recap`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(seriesTitle = "Yomira Destanı", readThroughChapter = -1.0, chapters = saga),
        )
        assertTrue(recap.storyBeats.isEmpty())
        assertTrue(recap.characters.isEmpty())
        assertEquals(4, recap.withheldChapters)
    }

    @Test
    fun `beats carry their chapter title and chapters without beats stay as landmarks`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(
                seriesTitle = "Yomira Destanı",
                readThroughChapter = 2.0,
                chapters = listOf(
                    chapter(1.0, "Başlangıç", listOf("Deniz kıyısında uyandı")),
                    chapter(2.0, "Yolculuk"),
                ),
            ),
        )
        assertEquals(listOf("Başlangıç - Deniz kıyısında uyandı", "Yolculuk"), recap.storyBeats)
    }

    @Test
    fun `characters are deduplicated across chapters keeping first appearance`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(
                seriesTitle = "Yomira Destanı",
                readThroughChapter = 3.0,
                chapters = listOf(
                    chapter(1.0, null, emptyList(), listOf("Arin", "MIRA")),
                    chapter(2.0, null, emptyList(), listOf("mira", "Bora")),
                    chapter(3.0, null, emptyList(), listOf("Arin")),
                ),
            ),
        )
        assertEquals(listOf("Arin", "MIRA", "Bora"), recap.characters)
    }

    @Test
    fun `unsorted chapters are ordered by their number`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(
                seriesTitle = "Yomira Destanı",
                readThroughChapter = 2.0,
                chapters = listOf(chapter(2.0, "İkinci"), chapter(1.0, "Birinci")),
            ),
        )
        assertEquals(listOf("Birinci", "İkinci"), recap.storyBeats)
    }

    @Test
    fun `blank notes and names are ignored`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(
                seriesTitle = "Yomira Destanı",
                readThroughChapter = 1.0,
                chapters = listOf(chapter(1.0, "  ", listOf("", "   "), listOf("", "  "))),
            ),
        )
        assertTrue(recap.storyBeats.isEmpty())
        assertTrue(recap.characters.isEmpty())
    }

    @Test
    fun `the premise travels separately so it is never chapter content`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(
                seriesTitle = "Yomira Destanı",
                readThroughChapter = 0.0,
                chapters = saga,
                premise = "Bir kahraman uyanır.",
                resumeChapter = 1.0,
            ),
        )
        assertEquals("Bir kahraman uyanır.", recap.premise)
        assertEquals(1.0, recap.resumeChapter)
        assertTrue(recap.storyBeats.isEmpty())
    }

    @Test
    fun `the recap is offered after a long break only`() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(SpoilerFreeRecapEngine.shouldOffer(lastReadAt = 100L, now = 100L + 8 * day))
        assertFalse(SpoilerFreeRecapEngine.shouldOffer(lastReadAt = 100L, now = 100L + 2 * day))
        assertFalse(SpoilerFreeRecapEngine.shouldOffer(lastReadAt = 0L, now = 100L + 30 * day))
        assertTrue(
            SpoilerFreeRecapEngine.shouldOffer(
                lastReadAt = 100L, now = 100L + 60_000, thresholdMillis = 30_000,
            ),
        )
    }

    @Test
    fun `fractional chapter numbers respect the horizon exactly`() {
        val recap = SpoilerFreeRecapEngine.build(
            RecapRequest(
                seriesTitle = "Yomira Destanı",
                readThroughChapter = 2.5,
                chapters = listOf(chapter(2.5, "Yarım"), chapter(3.0, "Üç")),
            ),
        )
        assertEquals(listOf("Yarım"), recap.storyBeats)
        assertEquals(1, recap.withheldChapters)
    }
}