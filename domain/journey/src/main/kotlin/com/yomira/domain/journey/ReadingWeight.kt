package com.yomira.domain.journey

import com.yomira.core.model.ReadingMode
import kotlin.math.roundToInt

/**
 * Fairness layer between formats.
 *
 * A 100-page western album and a 10-page manga chapter must not look equal in
 * the statistics. Pages are multiplied by a format factor and then normalised
 * to a **standard chapter of 20 weighted pages**. Long chapters therefore earn
 * proportionally more XP and badge progress, while completing a chapter adds a
 * small bonus so finishing still matters.
 *
 * Re-reads are deliberately worth 20%: they keep the habit loop alive without
 * letting a user farm the same chapter for the same XP forever.
 */
object ReadingWeight {
    const val STANDARD_PAGES_PER_CHAPTER = 20.0
    const val REREAD_XP_RATIO = 0.20

    fun pageFactor(mode: ReadingMode): Double = when (mode) {
        ReadingMode.MANGA_RTL -> 1.0
        ReadingMode.WEBTOON -> 0.82
        ReadingMode.WESTERN_LTR -> 1.18
    }

    fun chapterBonus(mode: ReadingMode): Int = when (mode) {
        ReadingMode.MANGA_RTL -> 12
        ReadingMode.WEBTOON -> 8
        ReadingMode.WESTERN_LTR -> 18
    }

    fun weightedPages(pages: Int, mode: ReadingMode): Double =
        pages.coerceAtLeast(0) * pageFactor(mode)

    fun readingUnits(pages: Int, mode: ReadingMode): Double =
        weightedPages(pages, mode) / STANDARD_PAGES_PER_CHAPTER

    /**
     * XP for one completed chapter. [pagesRead] should be the number of unique
     * pages visited, not the raw number of scroll events.
     */
    fun chapterXp(pagesRead: Int, mode: ReadingMode, firstRead: Boolean): Int {
        val readingUnits = readingUnits(pagesRead, mode)
        val base = readingUnits * 8.0 + chapterBonus(mode)
        val adjusted = if (firstRead) base else base * REREAD_XP_RATIO
        return adjusted.roundToInt().coerceAtLeast(1)
    }

    /** Reusable for ad-hoc page XP outside a chapter completion (e.g. tests/previews). */
    fun pageXp(pagesRead: Int, mode: ReadingMode, firstRead: Boolean): Int {
        val base = weightedPages(pagesRead, mode) * 0.4
        val adjusted = if (firstRead) base else base * REREAD_XP_RATIO
        return adjusted.roundToInt().coerceAtLeast(0)
    }
}
