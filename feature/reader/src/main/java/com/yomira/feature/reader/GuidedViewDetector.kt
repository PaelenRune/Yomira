package com.yomira.feature.reader

import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.guided.PanelBoundingBox
import com.yomira.core.ui.guided.ReadingOrder
import com.yomira.core.ui.guided.orderPanels

/**
 * Down-sampled grayscale of one page: 0 = black ink, 255 = paper. Row-major, one
 * byte per pixel. The detector only ever sees this grid, so panel detection runs
 * fully on-device with no network access and no heavyweight model.
 */
class LuminanceGrid(val width: Int, val height: Int, private val values: ByteArray) {
    init {
        require(width > 0 && height > 0) { "Grid dimensions must be positive" }
        require(values.size == width * height) { "Grid data must match its dimensions" }
    }

    operator fun get(x: Int, y: Int): Int = values[y * width + x].toInt() and 0xFF

    fun isPaper(x: Int, y: Int, backgroundThreshold: Int): Boolean = this[x, y] >= backgroundThreshold

    companion object {
        fun fromLuminances(width: Int, height: Int, luminances: IntArray): LuminanceGrid {
            require(luminances.size == width * height) { "Luminance data must match its dimensions" }
            return LuminanceGrid(width, height, ByteArray(luminances.size) { index ->
                luminances[index].coerceIn(0, 255).toByte()
            })
        }
    }
}

/** Tuning knobs of the panel detector; defaults suit 300dpi page scans. */
data class PanelDetectorConfig(
    /** Pixels at or above this luminance count as paper/gutter. */
    val backgroundThreshold: Int = 230,
    /**
     * Ink within this many grid pixels belongs to the same mass, so a panel
     * broken by a thin rule stays one box. A gutter wider than twice this stays
     * a real separation between two panels.
     */
    val mergeRadius: Int = 1,
    /** Boxes smaller than this fraction of the page are treated as noise. */
    val minPanelAreaFraction: Float = 0.004f,
    /** Panels covering more than this fraction of the page are kept as full page. */
    val fullPageAreaFraction: Float = 0.96f,
    /** Hard cap so a noisy page cannot flood guided view with micro panels. */
    val maxPanels: Int = 24,
) {
    init {
        require(backgroundThreshold in 1..255) { "Background threshold must be a luminance" }
        require(mergeRadius >= 0) { "Merge radius must not be negative" }
        require(minPanelAreaFraction > 0f && maxPanels > 0) { "Detector limits must be positive" }
    }
}

/**
 * Detects comic panel borders on a [LuminanceGrid] and returns their normalized
 * bounds in reading order.
 *
 * Pipeline: binarize ink, bridge [PanelDetectorConfig.mergeRadius] gaps with a
 * summed-area table, label connected ink masses, keep the ink extents of each
 * mass as a candidate box, drop noise, merge heavily overlapping boxes and
 * finally assign the reading sequence ([ReadingOrder] derived from the series'
 * reading mode). When nothing is drawn on the page the whole page is one panel
 * so guided view still works.
 */
object GuidedViewDetector {

    fun detect(
        grid: LuminanceGrid,
        readingOrder: ReadingOrder,
        config: PanelDetectorConfig = PanelDetectorConfig(),
    ): List<PanelBoundingBox> {
        val width = grid.width
        val height = grid.height
        val total = width * height

        // 1. Ink mask and summed-area table for the gap-bridging dilation.
        val ink = BooleanArray(total)
        val sat = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                val dark = grid[x, y] < config.backgroundThreshold
                ink[y * width + x] = dark
                if (dark) rowSum++
                sat[(y + 1) * (width + 1) + (x + 1)] = sat[y * (width + 1) + (x + 1)] + rowSum
            }
        }
        if (ink.none { it }) return listOf(PanelBoundingBox.fullPage())

        // 2. Dilated mass: any ink within the merge radius.
        val radius = config.mergeRadius
        val mass = BooleanArray(total)
        for (y in 0 until height) {
            val y0 = (y - radius).coerceAtLeast(0)
            val y1 = (y + radius).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(width - 1)
                mass[y * width + x] = areaSum(sat, width + 1, x0, y0, x1, y1) > 0
            }
        }

        // 3. Connected components over the mass, ink extents per component.
        val labels = IntArray(total) { -1 }
        val queue = IntArray(total)
        val boxes = ArrayList<PanelBoundingBox>()
        for (start in 0 until total) {
            if (!mass[start] || labels[start] != -1) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = boxes.size
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var inkCount = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                if (ink[index]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    inkCount++
                }
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        val neighbor = ny * width + nx
                        if (mass[neighbor] && labels[neighbor] == -1) {
                            labels[neighbor] = labels[start]
                            queue[tail++] = neighbor
                        }
                    }
                }
            }
            if (inkCount > 0) {
                boxes.add(pixelBox(minX, minY, maxX, maxY, width, height))
            }
        }
        if (boxes.isEmpty()) return listOf(PanelBoundingBox.fullPage())

        // 4. Drop noise specks, merge boxes that claim the same panel area.
        val minArea = config.minPanelAreaFraction * total
        val candidates = boxes.filter { it.area * total >= minArea || boxes.size == 1 }
            .let { if (it.isEmpty()) listOf(boxes.maxBy { box -> box.area }) else it }
        val merged = mergeOverlapping(candidates)
        val kept = merged.sortedByDescending { it.area }.take(config.maxPanels)
        if (kept.size == 1 && kept[0].area >= config.fullPageAreaFraction) {
            return listOf(PanelBoundingBox.fullPage())
        }
        return orderPanels(kept, readingOrder)
    }

    fun detect(
        grid: LuminanceGrid,
        mode: ReadingMode,
        config: PanelDetectorConfig = PanelDetectorConfig(),
    ): List<PanelBoundingBox> = detect(grid, ReadingOrder.of(mode), config)

    /** The panel reading sequence derived from the series' reading mode. */
    fun readingOrderFor(mode: ReadingMode): ReadingOrder = ReadingOrder.of(mode)

    private fun pixelBox(minX: Int, minY: Int, maxX: Int, maxY: Int, width: Int, height: Int) =
        PanelBoundingBox(
            left = minX / width.toFloat(),
            top = minY / height.toFloat(),
            right = (maxX + 1) / width.toFloat(),
            bottom = (maxY + 1) / height.toFloat(),
        )

    private fun mergeOverlapping(boxes: List<PanelBoundingBox>): List<PanelBoundingBox> {
        val result = boxes.map { it.copy(order = 0) }.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in result.indices) {
                for (j in i + 1 until result.size) {
                    if (overlapFraction(result[i], result[j]) > 0.55f) {
                        result[i] = union(result[i], result[j])
                        result.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }
        return result
    }

    private fun overlapFraction(a: PanelBoundingBox, b: PanelBoundingBox): Float {
        val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (width <= 0f || height <= 0f) return 0f
        return (width * height) / minOf(a.area, b.area)
    }

    private fun union(a: PanelBoundingBox, b: PanelBoundingBox) = PanelBoundingBox(
        left = minOf(a.left, b.left),
        top = minOf(a.top, b.top),
        right = maxOf(a.right, b.right),
        bottom = maxOf(a.bottom, b.bottom),
    )

    /** Sum over the inclusive pixel rectangle using a summed-area table. */
    private fun areaSum(sat: LongArray, stride: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long {
        val a = sat[y0 * stride + x0]
        val b = sat[y0 * stride + x1 + 1]
        val c = sat[(y1 + 1) * stride + x0]
        val d = sat[(y1 + 1) * stride + x1 + 1]
        return d - b - c + a
    }
}