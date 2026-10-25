package com.yomira.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebtoonSeamCleanerTest {

    /** Rows: 0 = ink, 255 = paper. Builds a full grid from per-row luminance. */
    private fun grid(width: Int, vararg rows: Int): LuminanceGrid {
        val values = ByteArray(width * rows.size) { index -> rows[index / width].toByte() }
        return LuminanceGrid(width, rows.size, values)
    }

    private fun rowGrid(width: Int, rows: List<Int>): LuminanceGrid {
        val values = ByteArray(width * rows.size)
        for (y in rows.indices) for (x in 0 until width) values[y * width + x] = rows[y].toByte()
        return LuminanceGrid(width, rows.size, values)
    }

    @Test
    fun `trims whitespace bands at both edges`() {
        val g = rowGrid(20, listOf(255, 255, 255, 20, 20, 20, 255, 255))
        val trim = WebtoonSeamCleaner.trimFor(g)
        assertEquals(3, trim.top)
        assertEquals(2, trim.bottom)
    }

    @Test
    fun `uniform compression hairline counts as a seam, not artwork`() {
        // A light uniform row (the splitter's line) between blank and ink.
        val g = rowGrid(20, listOf(255, 160, 20, 20, 255))
        val trim = WebtoonSeamCleaner.trimFor(g)
        assertTrue(trim.top >= 2) // whitespace + hairline
    }

    @Test
    fun `varied dark artwork is never trimmed`() {
        val g = rowGrid(20, listOf(255, 30, 200, 30, 200, 30, 255))
        val trim = WebtoonSeamCleaner.trimFor(g)
        assertEquals(1, trim.top)
        assertEquals(1, trim.bottom)
    }

    @Test
    fun `tiny blank bands are ignored`() {
        val g = rowGrid(20, listOf(255, 30, 30, 30, 30))
        val trim = WebtoonSeamCleaner.trimFor(g, WebtoonSeamConfig(minBlankFraction = 0.25f))
        assertEquals(0, trim.top)
    }

    @Test
    fun `trim never exceeds the configured fraction`() {
        val rows = List(20) { 255 }
        val g = rowGrid(20, rows)
        val trim = WebtoonSeamCleaner.trimFor(g)
        assertTrue(trim.top <= 20 * 0.35f + 1)
    }

    @Test
    fun `clean page with content throughout has no trim`() {
        val g = rowGrid(20, List(10) { 40 })
        val trim = WebtoonSeamCleaner.trimFor(g)
        assertEquals(0, trim.total)
    }
}