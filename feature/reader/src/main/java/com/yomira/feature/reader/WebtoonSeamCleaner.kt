package com.yomira.feature.reader

/**
 * Row-by-row seam and whitespace analysis for webtoon strips, fully on-device.
 *
 * Webtoon chapters are frequently served as several tall images that were cut
 * from one continuous strip; the cut introduces a compression seam line and a
 * band of unnecessary blank space. [WebtoonSeamCleaner] measures those bands so
 * the reader can collapse them before layout, keeping the vertical flow smooth.
 */
data class WebtoonSeamConfig(
    /** Rows whose darkest pixel is at or above this are considered blank. */
    val blankThreshold: Int = 236,
    /** A seam line is this uniform across its width (max minus min luminance). */
    val seamUniformity: Int = 14,
    /** Seam lines are never pure white; they sit in this luminance window. */
    val seamMinLuminance: Int = 120,
    /** Ignore blank bands thinner than this fraction of the image height. */
    val minBlankFraction: Float = 0.004f,
    /** Never crop more than this fraction of an image from a single side. */
    val maxTrimFraction: Float = 0.35f,
) {
    init {
        require(blankThreshold in 1..255) { "Blank threshold must be a luminance" }
        require(seamUniformity >= 0) { "Seam uniformity must not be negative" }
        require(minBlankFraction in 0f..1f && maxTrimFraction in 0f..1f) {
            "Trim fractions must be within 0..1"
        }
    }
}

/** Measured whitespace on one side of an image, in pixels. */
data class TrimSpec(val top: Int, val bottom: Int) {
    init {
        require(top >= 0 && bottom >= 0) { "Trim must not be negative" }
    }

    val total: Int get() = top + bottom
}

object WebtoonSeamCleaner {

    /**
     * Measures the blank (and seam-line) bands at the top and bottom of [grid].
     *
     * A row is blank when every pixel is paper; a row is a seam line when it is
     * nearly uniform and darker than paper but lighter than typical artwork.
     * Contiguous blank and seam rows form one trimmable band per side.
     */
    fun trimFor(grid: LuminanceGrid, config: WebtoonSeamConfig = WebtoonSeamConfig()): TrimSpec {
        val top = leadingBlankRows(grid, config)
        val bottom = trailingBlankRows(grid, config, skip = top)
        // Round the per-side budget up: a cap of "no more than the fraction"
        // must not discard a whole pixel of a legitimately blank band.
        val limitTop = kotlin.math.ceil(grid.height * config.maxTrimFraction).toInt()
        val trimmedTop = top.coerceAtMost(limitTop)
        val limitBottom = minOf(limitTop, grid.height - trimmedTop - 1)
        return TrimSpec(
            top = trimmedTop.coerceAtMost(grid.height - 1),
            bottom = bottom.coerceIn(0, maxOf(0, limitBottom)),
        )
    }

    private fun leadingBlankRows(grid: LuminanceGrid, config: WebtoonSeamConfig): Int {
        var rows = 0
        for (y in 0 until grid.height) {
            if (isBlankRow(grid, y, config)) rows = y + 1 else break
        }
        return if (rows < grid.height * config.minBlankFraction) 0 else rows
    }

    private fun trailingBlankRows(grid: LuminanceGrid, config: WebtoonSeamConfig, skip: Int): Int {
        var rows = 0
        for (y in grid.height - 1 downTo skip) {
            if (isBlankRow(grid, y, config)) rows++ else break
        }
        return if (rows < grid.height * config.minBlankFraction) 0 else rows
    }

    /** A row is blank when it is paper throughout, or a uniform compression seam. */
    private fun isBlankRow(grid: LuminanceGrid, y: Int, config: WebtoonSeamConfig): Boolean {
        var min = 255
        var max = 0
        var paper = true
        for (x in 0 until grid.width) {
            val value = grid[x, y]
            if (value < config.blankThreshold) paper = false
            if (value < min) min = value
            if (value > max) max = value
            if (!paper && max - min > config.seamUniformity) return false
        }
        if (paper) return true
        // A seam line: uniform, not black artwork, lighter than any real drawing.
        return max - min <= config.seamUniformity && min >= config.seamMinLuminance
    }
}
