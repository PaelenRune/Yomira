package com.yomira.core.ui.guided

import com.yomira.core.model.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelGeometryTest {

    private fun box(left: Float, top: Float, right: Float, bottom: Float) =
        PanelBoundingBox(left, top, right, bottom)

    @Test
    fun `panel boxes validate their geometry`() {
        assertEquals(0.5f, box(0f, 0f, 0.5f, 1f).width)
        assertEquals(1f, box(0f, 0f, 0.5f, 1f).height)
        assertEquals(0.25f, box(0f, 0f, 0.5f, 1f).centerX)
        assertEquals(0.5f, box(0f, 0f, 0.5f, 1f).centerY)
        assertEquals(0.5f, box(0f, 0f, 0.5f, 1f).aspectRatio)
        val invalid = listOf<() -> PanelBoundingBox>(
            { box(-0.1f, 0f, 0.5f, 1f) },
            { box(0.5f, 0f, 0.5f, 1f) },
            { box(0f, 0.8f, 1f, 0.4f) },
            { box(0f, 0f, 1.2f, 1f) },
            { box(0f, 0f, 1f, 1f).copy(order = -1) },
        )
        for (construct in invalid) {
            // The constructor itself must reject degenerate boxes.
            try {
                construct()
                throw AssertionError("Expected invalid box to be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun `inset shrinks toward the panel center and stays on the page`() {
        val inset = box(0.2f, 0.2f, 0.8f, 0.8f).inset(0.1f)
        assertTrue(inset.left > 0.2f && inset.right < 0.8f)
        assertEquals(0.5f, inset.centerX, 1e-4f)
        assertEquals(0.5f, inset.centerY, 1e-4f)
        assertEquals(box(0.2f, 0.2f, 0.8f, 0.8f), box(0.2f, 0.2f, 0.8f, 0.8f).inset(0f))
    }

    @Test
    fun `manga orders panels right to left`() {
        val left = box(0.05f, 0.05f, 0.45f, 0.45f)
        val right = box(0.55f, 0.05f, 0.95f, 0.45f)
        val ordered = orderPanels(listOf(left, right), ReadingOrder.RIGHT_TO_LEFT)
        assertEquals(listOf(right.left, left.left), ordered.map { it.left })
        assertEquals(listOf(0, 1), ordered.map { it.order })
    }

    @Test
    fun `western comics order panels left to right`() {
        val left = box(0.05f, 0.05f, 0.45f, 0.45f)
        val right = box(0.55f, 0.05f, 0.95f, 0.45f)
        val ordered = orderPanels(listOf(right, left), ReadingOrder.LEFT_TO_RIGHT)
        assertEquals(listOf(left.left, right.left), ordered.map { it.left })
    }

    @Test
    fun `panels stack in rows before crossing the page`() {
        val firstRow = box(0.55f, 0.02f, 0.98f, 0.40f)
        val secondRowLeft = box(0.02f, 0.45f, 0.48f, 0.95f)
        val secondRowRight = box(0.52f, 0.45f, 0.98f, 0.95f)
        val ordered = orderPanels(
            listOf(secondRowLeft, secondRowRight, firstRow),
            ReadingOrder.RIGHT_TO_LEFT,
        )
        assertEquals(listOf(firstRow.top, secondRowRight.top, secondRowLeft.top), ordered.map { it.top })
    }

    @Test
    fun `reading order follows the series reading mode`() {
        assertEquals(ReadingOrder.RIGHT_TO_LEFT, ReadingOrder.of(ReadingMode.MANGA_RTL))
        assertEquals(ReadingOrder.LEFT_TO_RIGHT, ReadingOrder.of(ReadingMode.WESTERN_LTR))
        assertEquals(ReadingOrder.LEFT_TO_RIGHT, ReadingOrder.of(ReadingMode.WEBTOON))
    }

    @Test
    fun `overlapping bands stay in one row`() {
        val tall = box(0.05f, 0.10f, 0.35f, 0.60f)
        val beside = box(0.45f, 0.30f, 0.95f, 0.55f)
        val ordered = orderPanels(listOf(beside, tall), ReadingOrder.RIGHT_TO_LEFT)
        assertEquals(listOf(beside.left, tall.left), ordered.map { it.left })
    }
}