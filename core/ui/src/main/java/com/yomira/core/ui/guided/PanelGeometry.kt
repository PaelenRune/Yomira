package com.yomira.core.ui.guided

import androidx.compose.runtime.Immutable
import com.yomira.core.model.ReadingMode

/**
 * The direction the eye travels between panels of a single page.
 * Manga is read right to left, Western comics left to right; webtoon strips
 * are a single vertical column so left-to-right keeps row ordering stable.
 */
enum class ReadingOrder {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT;

    companion object {
        /** The panel order follows the series' reading mode automatically. */
        fun of(mode: ReadingMode): ReadingOrder = when (mode) {
            ReadingMode.MANGA_RTL -> RIGHT_TO_LEFT
            ReadingMode.WESTERN_LTR, ReadingMode.WEBTOON -> LEFT_TO_RIGHT
        }
    }
}

/**
 * Border of one comic panel in normalized page coordinates: every value is a
 * fraction of the page (0..1) so the same box drives zooming at any resolution.
 * [order] is the zero-based reading sequence assigned by [orderPanels].
 */
@Immutable
data class PanelBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val order: Int = 0,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "Panel coordinates must be normalized to the page (0..1)"
        }
        require(left < right && top < bottom) { "Panel must have a positive area" }
        require(order >= 0) { "Panel order must not be negative" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val aspectRatio: Float get() = width / height

    /** Shrinks the box by [fraction] of its size on each axis, staying inside the page. */
    fun inset(fraction: Float): PanelBoundingBox {
        require(fraction in 0f..0.5f) { "Inset fraction must be within 0..0.5" }
        val dx = width * fraction
        val dy = height * fraction
        return copy(
            left = (left + dx).coerceIn(0f, 1f),
            top = (top + dy).coerceIn(0f, 1f),
            right = (right - dx).coerceIn(0f, 1f),
            bottom = (bottom - dy).coerceIn(0f, 1f),
        )
    }

    companion object {
        /** Fallback box used when no panel border can be detected on the page. */
        fun fullPage(order: Int = 0) = PanelBoundingBox(0f, 0f, 1f, 1f, order)
    }
}

/**
 * Assigns the reading sequence to unsorted panel boxes.
 *
 * Panels are grouped into horizontal bands: a box joins the current band when at
 * least [rowOverlap] of the smaller box overlaps it vertically. Inside a band the
 * boxes sort by their horizontal center — descending for manga (right to left),
 * ascending for Western comics. The returned list is in reading order and every
 * box carries its sequential [PanelBoundingBox.order].
 */
fun orderPanels(
    boxes: List<PanelBoundingBox>,
    readingOrder: ReadingOrder,
    rowOverlap: Float = 0.5f,
): List<PanelBoundingBox> {
    require(rowOverlap in 0f..1f) { "Row overlap tolerance must be within 0..1" }
    if (boxes.isEmpty()) return emptyList()
    val rows = mutableListOf<MutableList<PanelBoundingBox>>()
    val bandTop = mutableListOf<Float>()
    val bandBottom = mutableListOf<Float>()
    for (box in boxes.sortedBy { it.top }) {
        val rowIndex = rows.lastIndex
        val joins = rowIndex >= 0 && verticalOverlap(box, bandTop[rowIndex], bandBottom[rowIndex]) >=
            rowOverlap * minOf(box.height, bandBottom[rowIndex] - bandTop[rowIndex])
        if (joins) {
            rows[rowIndex].add(box)
            bandTop[rowIndex] = minOf(bandTop[rowIndex], box.top)
            bandBottom[rowIndex] = maxOf(bandBottom[rowIndex], box.bottom)
        } else {
            rows.add(mutableListOf(box))
            bandTop.add(box.top)
            bandBottom.add(box.bottom)
        }
    }
    return rows
        .map { row ->
            when (readingOrder) {
                ReadingOrder.LEFT_TO_RIGHT -> row.sortedBy { it.centerX }
                ReadingOrder.RIGHT_TO_LEFT -> row.sortedByDescending { it.centerX }
            }
        }
        .flatten()
        .mapIndexed { index, box -> box.copy(order = index) }
}

private fun verticalOverlap(box: PanelBoundingBox, bandTop: Float, bandBottom: Float): Float {
    val top = maxOf(box.top, bandTop)
    val bottom = minOf(box.bottom, bandBottom)
    return (bottom - top).coerceAtLeast(0f)
}
