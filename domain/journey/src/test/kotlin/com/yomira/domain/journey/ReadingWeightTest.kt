package com.yomira.domain.journey

import com.yomira.core.model.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingWeightTest {
    @Test fun `a hundred page western album outweighs a ten page manga chapter`() {
        val western = ReadingWeight.readingUnits(100, ReadingMode.WESTERN_LTR)
        val manga = ReadingWeight.readingUnits(10, ReadingMode.MANGA_RTL)
        assertTrue("western=$western manga=$manga", western > manga * 10)
    }

    @Test fun `standard chapter is twenty weighted pages`() {
        assertEquals(1.0, ReadingWeight.readingUnits(20, ReadingMode.MANGA_RTL), 0.0001)
        assertEquals(1.18, ReadingWeight.readingUnits(20, ReadingMode.WESTERN_LTR), 0.0001)
    }

    @Test fun `reread pays only a fifth of the first read xp`() {
        val first = ReadingWeight.chapterXp(20, ReadingMode.MANGA_RTL, firstRead = true)
        val again = ReadingWeight.chapterXp(20, ReadingMode.MANGA_RTL, firstRead = false)
        assertEquals((first * ReadingWeight.REREAD_XP_RATIO).toInt(), again)
        assertTrue(again < first)
    }

    @Test fun `xp always rewards at least one point`() {
        assertTrue(ReadingWeight.chapterXp(1, ReadingMode.WEBTOON, firstRead = true) >= 1)
        assertTrue(ReadingWeight.pageXp(1, ReadingMode.WEBTOON, firstRead = false) >= 0)
    }

    @Test fun `webtoon is slightly lighter than manga for the same page count`() {
        assertTrue(
            ReadingWeight.weightedPages(40, ReadingMode.WEBTOON) <
                ReadingWeight.weightedPages(40, ReadingMode.MANGA_RTL),
        )
    }
}
