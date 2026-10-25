package com.yomira.feature.reader

import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.guided.ReadingOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadStitcherTest {

    private fun page(
        index: Int,
        width: Int,
        height: Int,
        leftEdgeInk: Float? = null,
        rightEdgeInk: Float? = null,
    ) = PageMetrics(index, width, height, leftEdgeInk, rightEdgeInk)

    @Test
    fun `portrait pages are single groups`() {
        val pages = listOf(page(0, 700, 1000), page(1, 700, 1000), page(2, 700, 1000))
        val groups = SpreadStitcher.detect(pages, ReadingOrder.LEFT_TO_RIGHT)
        assertEquals(3, groups.size)
        assertTrue(groups.all { it is SpreadGroup.Single })
    }

    @Test
    fun `landscape page is recognised as one wide scene`() {
        val groups = SpreadStitcher.detect(listOf(page(0, 1600, 900)), ReadingOrder.LEFT_TO_RIGHT)
        assertEquals(listOf(SpreadGroup.Wide(0)), groups)
    }

    @Test
    fun `wider than typical neighbours become a spread pair`() {
        // Two halves wider than the 0.66 typical page, same height, landscape combined.
        val halves = listOf(page(0, 780, 1000), page(1, 780, 1000))
        val groups = SpreadStitcher.detect(halves, ReadingOrder.RIGHT_TO_LEFT)
        assertEquals(1, groups.size)
        val dual = groups[0] as SpreadGroup.Dual
        assertEquals(0, dual.firstPage)
        assertEquals(1, dual.secondPage)
        assertEquals(SpreadConfidence.ASPECT_ONLY, dual.confidence)
    }

    @Test
    fun `edge ink evidence upgrades confidence and rejects uncut edges`() {
        val cut = page(0, 780, 1000, leftEdgeInk = 0.6f, rightEdgeInk = 0.1f)
        val cutOther = page(1, 780, 1000, leftEdgeInk = 0.1f, rightEdgeInk = 0.6f)
        val dual = SpreadStitcher.detect(listOf(cut, cutOther), ReadingOrder.RIGHT_TO_LEFT)[0]
        assertEquals(SpreadConfidence.ASPECT_AND_EDGE, (dual as SpreadGroup.Dual).confidence)

        // When the sampler says the facing edges are clean margins, no spread.
        val clean = page(0, 780, 1000, leftEdgeInk = 0.02f)
        val cleanOther = page(1, 780, 1000, rightEdgeInk = 0.02f)
        val groups = SpreadStitcher.detect(listOf(clean, cleanOther), ReadingOrder.RIGHT_TO_LEFT)
        assertTrue(groups.all { it is SpreadGroup.Single })
    }

    @Test
    fun `height mismatch blocks a spread`() {
        val groups = SpreadStitcher.detect(
            listOf(page(0, 780, 1000), page(1, 780, 1200)),
            ReadingOrder.LEFT_TO_RIGHT,
        )
        assertTrue(groups.all { it is SpreadGroup.Single })
    }

    @Test
    fun `stitched layout abuts exactly with no seam`() {
        val layout = SpreadStitcher.stitchLayout(page(0, 700, 1000), page(1, 700, 1000), ReadingOrder.LEFT_TO_RIGHT, 1200, 800)
        assertEquals(layout.first.right, layout.second.left)
        assertEquals(layout.first.top, layout.second.top)
        assertEquals(layout.first.bottom, layout.second.bottom)
        assertEquals(layout.width, layout.first.width + layout.second.width)
    }

    @Test
    fun `manga places the first page on the right`() {
        val ltr = SpreadStitcher.stitchLayout(page(0, 700, 1000), page(1, 700, 1000), ReadingOrder.LEFT_TO_RIGHT, 1200, 800)
        val rtl = SpreadStitcher.stitchLayout(page(0, 700, 1000), page(1, 700, 1000), ReadingOrder.RIGHT_TO_LEFT, 1200, 800)
        assertTrue(ltr.first.left < ltr.second.left)
        assertTrue(rtl.first.left > rtl.second.left)
    }

    @Test
    fun `stitched scene always fits the container`() {
        val layout = SpreadStitcher.stitchLayout(page(0, 1400, 1000), page(1, 1400, 1000), ReadingOrder.LEFT_TO_RIGHT, 1000, 1000)
        assertTrue(layout.width <= 1000)
        assertTrue(layout.height <= 1000)
    }

    @Test
    fun `webtoon pages never form spreads`() {
        val groups = SpreadStitcher.detect(listOf(page(0, 800, 2000), page(1, 800, 2000)), ReadingOrder.LEFT_TO_RIGHT)
        assertTrue(groups.all { it is SpreadGroup.Single })
    }

    @Test
    fun `reading order adapts to the series mode`() {
        assertEquals(ReadingOrder.RIGHT_TO_LEFT, ReadingOrder.of(ReadingMode.MANGA_RTL))
        assertEquals(ReadingOrder.LEFT_TO_RIGHT, ReadingOrder.of(ReadingMode.WESTERN_LTR))
        assertFalse(SpreadStitcher.detect(listOf(page(0, 700, 1000)), ReadingOrder.of(ReadingMode.WEBTOON)).any { it is SpreadGroup.Dual })
    }
}