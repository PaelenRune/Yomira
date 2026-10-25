package com.yomira.feature.reader

import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.guided.ReadingOrder

/**
 * Native dimensions of one decoded page, used for spread detection.
 * Pure data: detection runs on the JVM and needs no Android types.
 *
 * [leftEdgeInk] / [rightEdgeInk] are optional ink fractions of the outer edge
 * columns, supplied by the pixel sampler when it measured them. They let the
 * detector confirm a guess from the cut edges; without them detection falls
 * back to aspect evidence alone.
 */
data class PageMetrics(
    val index: Int,
    val width: Int,
    val height: Int,
    val leftEdgeInk: Float? = null,
    val rightEdgeInk: Float? = null,
) {
    init {
        require(width > 0 && height > 0) { "Page dimensions must be positive" }
        require(index >= 0) { "Page index must not be negative" }
    }

    val aspectRatio: Float get() = width.toFloat() / height
}

/** How sure the stitcher is that two pages form one split double-page scene. */
enum class SpreadConfidence { ASPECT_ONLY, ASPECT_AND_EDGE }

/** How one logical scene is laid out from one or two source pages. */
sealed interface SpreadGroup {
    val indices: List<Int>

    data class Single(val index: Int) : SpreadGroup {
        override val indices: List<Int> = listOf(index)
    }

    /** One image that already spans a full spread (landscape art). */
    data class Wide(val index: Int) : SpreadGroup {
        override val indices: List<Int> = listOf(index)
    }

    /**
     * Two pages that form one wide scene. [firstPage] is the page that comes
     * first in the file; [readingRightToLeft] tells the stitcher which physical
     * side each belongs on (manga spreads read right to left).
     */
    data class Dual(
        val firstPage: Int,
        val secondPage: Int,
        val readingRightToLeft: Boolean,
        val confidence: SpreadConfidence = SpreadConfidence.ASPECT_ONLY,
    ) : SpreadGroup {
        init {
            require(secondPage > firstPage) { "Stitched pages must be consecutive" }
        }

        override val indices: List<Int> = listOf(firstPage, secondPage)
    }
}

/** Tuning knobs of the aspect-ratio based spread detector. */
data class SpreadConfig(
    /** A page at or above this ratio already is one wide scene. */
    val widePageAspect: Float = 1.15f,
    /** Split halves are wider than the chapter's typical page by this factor. */
    val halfWideBias: Float = 1.06f,
    /** Halves of one drawing share their height within this tolerance. */
    val heightTolerance: Float = 0.05f,
    /** ...and their shape, within this tolerance. */
    val aspectTolerance: Float = 0.08f,
    /** Joined, the halves must look like a landscape scene. */
    val combinedMinAspect: Float = 1.15f,
    /** ...but not like a whole chapter strip. */
    val combinedMaxAspect: Float = 2.6f,
    /** An edge with at least this much ink was cut through artwork. */
    val edgeInkMin: Float = 0.25f,
    /** Portrait ratio used when a chapter gives no better baseline. */
    val typicalAspect: Float = 0.66f,
) {
    init {
        require(widePageAspect > 1f) { "Wide pages must be landscape" }
        require(halfWideBias > 0f && heightTolerance >= 0f && aspectTolerance >= 0f) {
            "Spread tolerances must not be negative"
        }
        require(combinedMinAspect < combinedMaxAspect) { "Spread aspect window is inverted" }
        require(edgeInkMin in 0f..1f) { "Edge ink is a fraction" }
    }
}

/**
 * Detects double-page spreads split across two page images and plans their
 * seam-free placement.
 *
 * Detection works on aspect ratios: a page already landscape is a
 * [SpreadGroup.Wide]; two adjacent portrait pages that are both wider than the
 * chapter's typical page and whose heights match are halves of one scene. When
 * the pixel sampler supplied inner-edge ink fractions, the two facing cut edges
 * must confirm the guess â€” otherwise ordinary pages are never merged by
 * accident (fail-safe, not fail-open).
 */
object SpreadStitcher {

    fun detect(
        pages: List<PageMetrics>,
        order: ReadingOrder,
        config: SpreadConfig = SpreadConfig(),
    ): List<SpreadGroup> {
        val groups = ArrayList<SpreadGroup>()
        val typical = typicalAspect(pages, config)
        var i = 0
        while (i < pages.size) {
            val page = pages[i]
            if (page.aspectRatio >= config.widePageAspect) {
                groups.add(SpreadGroup.Wide(page.index))
                i++
                continue
            }
            val next = pages.getOrNull(i + 1)
            val dual = if (next == null) null else asSpread(page, next, order, typical, config)
            if (dual != null) {
                groups.add(dual)
                i += 2
            } else {
                groups.add(SpreadGroup.Single(page.index))
                i++
            }
        }
        return groups
    }

    private fun asSpread(
        first: PageMetrics,
        second: PageMetrics,
        order: ReadingOrder,
        typical: Float,
        config: SpreadConfig,
    ): SpreadGroup.Dual? {
        if (first.aspectRatio >= config.widePageAspect || second.aspectRatio >= config.widePageAspect) return null
        val heightDelta = kotlin.math.abs(first.height - second.height) / maxOf(first.height, second.height).toFloat()
        if (heightDelta > config.heightTolerance) return null
        if (kotlin.math.abs(first.aspectRatio - second.aspectRatio) > config.aspectTolerance) return null
        val combined = (first.width + second.width) / maxOf(first.height, second.height).toFloat()
        if (combined < config.combinedMinAspect || combined > config.combinedMaxAspect) return null

        val aspectEvidence = first.aspectRatio >= typical * config.halfWideBias &&
            second.aspectRatio >= typical * config.halfWideBias
        // The facing cut edges: in RTL the earlier page is the right half, so
        // its cut edge is the left one (and the mirror for the second page).
        val firstInk = facingInk(first, secondSide = false, order)
        val secondInk = facingInk(second, secondSide = true, order)
        val edgeMeasured = firstInk != null && secondInk != null
        val edgeConfirmed = edgeMeasured &&
            firstInk!! >= config.edgeInkMin && secondInk!! >= config.edgeInkMin

        return when {
            // Measured edges must confirm the guess: clean margins next to the
            // fold mean these are ordinary pages (fail-safe, not fail-open).
            edgeMeasured -> if (edgeConfirmed) {
                SpreadGroup.Dual(first.index, second.index, order == ReadingOrder.RIGHT_TO_LEFT, SpreadConfidence.ASPECT_AND_EDGE)
            } else {
                null
            }
            aspectEvidence -> SpreadGroup.Dual(first.index, second.index, order == ReadingOrder.RIGHT_TO_LEFT, SpreadConfidence.ASPECT_ONLY)
            else -> null
        }
    }

    /** Ink fraction of the edge this page would have been cut along. */
    private fun facingInk(page: PageMetrics, secondSide: Boolean, order: ReadingOrder): Float? {
        val firstCutLeft = order == ReadingOrder.RIGHT_TO_LEFT
        val cutLeft = if (secondSide) !firstCutLeft else firstCutLeft
        return if (cutLeft) page.leftEdgeInk else page.rightEdgeInk
    }

    /**
     * The chapter's typical portrait page. A baseline needs at least three
     * samples to be meaningful; on a chapter that is nothing but spread halves
     * the conventional portrait ratio is the safer reference.
     */
    private fun typicalAspect(pages: List<PageMetrics>, config: SpreadConfig): Float {
        val portrait = pages.filter { it.aspectRatio < config.widePageAspect }
            .map { it.aspectRatio }.sorted()
        if (portrait.size < 3) return config.typicalAspect
        val median = portrait[portrait.size / 2]
        return if (median > 0.2f) median else config.typicalAspect
    }

    /**
     * Pixel layout of a stitched pair. Both halves share one output height and
     * abut exactly â€” `second.left == first.right` â€” so no seam line can appear.
     * Rounding leftovers go to the wider half, keeping the total inside the
     * container.
     */
    fun stitchLayout(
        first: PageMetrics,
        second: PageMetrics,
        order: ReadingOrder,
        containerWidth: Int,
        containerHeight: Int,
    ): StitchLayout {
        require(containerWidth > 0 && containerHeight > 0) { "Container size must be positive" }
        val naturalWidth = first.width + second.width
        val naturalHeight = maxOf(first.height, second.height)
        val scale = minOf(
            containerWidth.toFloat() / naturalWidth,
            containerHeight.toFloat() / naturalHeight,
        )
        val outHeight = (naturalHeight * scale).toInt().coerceAtLeast(1)
        val outTotal = (naturalWidth * scale).toInt().coerceAtLeast(2)
        val firstWidth = (first.width * scale).toInt().coerceIn(1, outTotal - 1)
        val secondWidth = outTotal - firstWidth
        val leftWidth = if (order == ReadingOrder.LEFT_TO_RIGHT) firstWidth else secondWidth
        val startLeft = (containerWidth - outTotal) / 2
        val top = (containerHeight - outHeight) / 2
        val leftRect = RectI(startLeft, top, startLeft + leftWidth, top + outHeight)
        val rightRect = RectI(startLeft + leftWidth, top, startLeft + outTotal, top + outHeight)
        return StitchLayout(
            width = outTotal,
            height = outHeight,
            first = if (order == ReadingOrder.LEFT_TO_RIGHT) leftRect else rightRect,
            second = if (order == ReadingOrder.LEFT_TO_RIGHT) rightRect else leftRect,
        )
    }

    /**
     * Plans the pager slots from the geometry the reader has measured so far.
     *
     * A page whose size is still unknown is rendered on its own; detection only
     * runs once the whole chapter has been seen, otherwise an early measurement
     * could pair two pages that later turn out not to be a spread.
     */
    fun plan(geometry: List<PageGeometry?>, mode: ReadingMode): List<SpreadSlot> {
        val singles = geometry.indices.map { SpreadSlot.Single(it) }
        if (mode == ReadingMode.WEBTOON) return singles
        val metrics = geometry.map { it?.toMetrics() ?: return singles }
        return detect(metrics, ReadingOrder.of(mode)).map { group ->
            when (group) {
                is SpreadGroup.Dual -> SpreadSlot.Stitched(group.firstPage, group.secondPage)
                is SpreadGroup.Single -> SpreadSlot.Single(group.index)
                is SpreadGroup.Wide -> SpreadSlot.Single(group.index)
            }
        }
    }
}

/** Decoded page size captured from the image loader, keyed by page index. */
data class PageGeometry(val index: Int, val width: Int, val height: Int) {
    init {
        require(index >= 0) { "Page index must not be negative" }
        require(width > 0 && height > 0) { "Page dimensions must be positive" }
    }

    fun toMetrics(): PageMetrics = PageMetrics(index = index, width = width, height = height)
}

/** One pager slot: a single page or two pages stitched into one scene. */
sealed interface SpreadSlot {
    val firstPage: Int
    val lastPage: Int

    data class Single(val page: Int) : SpreadSlot {
        override val firstPage: Int get() = page
        override val lastPage: Int get() = page
    }

    data class Stitched(val leftPage: Int, val rightPage: Int) : SpreadSlot {
        override val firstPage: Int get() = minOf(leftPage, rightPage)
        override val lastPage: Int get() = maxOf(leftPage, rightPage)
    }
}

/** Integer rectangle in device pixels; the stitcher never leaves fractional gaps. */
data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(right >= left && bottom >= top) { "Rectangle must not be inverted" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class StitchLayout(val width: Int, val height: Int, val first: RectI, val second: RectI) {
    init {
        require(width > 0 && height > 0) { "Stitched scene must be visible" }
    }

    /** The seam between the halves: zero-width, exactly where they abut. */
    val seamLeft: Int get() = minOf(first.right, second.right)
}

