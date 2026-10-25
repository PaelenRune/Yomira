package com.yomira.feature.reader

import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.guided.ReadingOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedViewDetectorTest {

    /** Draws axis-aligned ink rectangles onto a paper grid. */
    private fun page(width: Int, height: Int, vararg panels: IntArray): LuminanceGrid {
        val values = ByteArray(width * height) { 250.toByte() }
        for (panel in panels) {
            val (left, top, right, bottom) = panel
            for (y in top until bottom) {
                for (x in left until right) {
                    values[y * width + x] = if (x == left || x == right - 1 || y == top || y == bottom - 1) 20
                    else values[y * width + x]
                }
            }
        }
        return LuminanceGrid(width, height, values)
    }

    @Test
    fun `blank page falls back to a single full page panel`() {
        val grid = LuminanceGrid(40, 60, ByteArray(40 * 60) { 255.toByte() })
        val panels = GuidedViewDetector.detect(grid, ReadingOrder.LEFT_TO_RIGHT)
        assertEquals(1, panels.size)
        assertEquals(0f, panels[0].left)
        assertEquals(1f, panels[0].bottom)
        assertEquals(0, panels[0].order)
    }

    @Test
    fun `detects the borders of a three panel manga page`() {
        // Two stacked rows: one wide top panel, two bottom panels.
        val grid = page(
            100, 150,
            intArrayOf(4, 4, 96, 66),
            intArrayOf(4, 74, 48, 146),
            intArrayOf(52, 74, 96, 146),
        )
        val panels = GuidedViewDetector.detect(grid, ReadingOrder.RIGHT_TO_LEFT)
        assertEquals(3, panels.size)
        // Manga reading order: the top wide panel first, then right, then left.
        assertTrue(panels[0].top < 0.1f)
        assertTrue(panels[1].centerX > 0.5f)
        assertTrue(panels[2].centerX < 0.5f)
        assertEquals(listOf(0, 1, 2), panels.map { it.order })
    }

    @Test
    fun `reading order flips for western pages`() {
        val grid = page(
            100, 100,
            intArrayOf(4, 4, 48, 96),
            intArrayOf(52, 4, 96, 96),
        )
        val western = GuidedViewDetector.detect(grid, ReadingOrder.LEFT_TO_RIGHT)
        val manga = GuidedViewDetector.detect(grid, ReadingOrder.RIGHT_TO_LEFT)
        assertTrue(western[0].centerX < western[1].centerX)
        assertTrue(manga[0].centerX > manga[1].centerX)
    }

    @Test
    fun `small noise specks never become panels`() {
        val grid = page(100, 100, intArrayOf(10, 10, 60, 90))
        // Sprinkle isolated pixels far from the real panel.
        val values = ByteArray(100 * 100) { 250.toByte() }
        for (y in 0 until 100) for (x in 0 until 100) values[y * 100 + x] = grid[x, y].toByte()
        for (i in 0 until 6) values[(30 + i) * 100 + 85] = 30
        val noisy = LuminanceGrid(100, 100, values)
        val panels = GuidedViewDetector.detect(noisy, ReadingOrder.LEFT_TO_RIGHT)
        assertEquals(1, panels.size)
        assertTrue(panels[0].right < 0.75f)
    }

    @Test
    fun `detection honors the series reading mode`() {
        val grid = page(100, 100, intArrayOf(4, 4, 96, 96))
        assertEquals(
            GuidedViewDetector.detect(grid, ReadingOrder.RIGHT_TO_LEFT).map { it.order },
            GuidedViewDetector.detect(grid, ReadingMode.MANGA_RTL).map { it.order },
        )
        assertEquals(ReadingOrder.RIGHT_TO_LEFT, GuidedViewDetector.readingOrderFor(ReadingMode.MANGA_RTL))
        assertEquals(ReadingOrder.LEFT_TO_RIGHT, GuidedViewDetector.readingOrderFor(ReadingMode.WESTERN_LTR))
    }

    @Test
    fun `grid validation rejects malformed buffers`() {
        try {
            LuminanceGrid(4, 4, ByteArray(10))
            throw AssertionError("Expected malformed grid to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }
}