package com.yomira.desktop.state

import kotlin.math.min

/**
 * Desktop-only presentation math. Kept free of Compose types so it can be unit
 * tested without a window server.
 */
object PosterGrid {
    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 8

    /** Smallest poster column that still reads as a "hero" card on a desktop display. */
    const val MIN_COLUMN_WIDTH_DP = 260f

    /**
     * Poster columns for the available content width.
     *
     * The breakpoints deliberately land at the common Windows display widths:
     * 1920 -> 6, 2560 (2K) -> 7, 3440 (ultrawide) / 3840 (4K) -> 8. Narrow
     * windows degrade to 2-5 columns instead of producing unreadable slivers.
     * A minimum card width caps the breakpoint count so a very wide, very short
     * window never turns into a wall of stamp-sized covers.
     */
    fun columnsForWidth(widthDp: Float): Int {
        if (!widthDp.isFinite() || widthDp <= 0f) return MIN_COLUMNS
        val byBreakpoint = when {
            widthDp >= 3_400f -> 8
            widthDp >= 2_560f -> 7
            widthDp >= 1_900f -> 6
            widthDp >= 1_500f -> 5
            widthDp >= 1_120f -> 4
            widthDp >= 780f -> 3
            else -> 2
        }
        val byMinimumWidth = (widthDp / MIN_COLUMN_WIDTH_DP).toInt()
        return min(byBreakpoint, byMinimumWidth).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }
}

/**
 * The page the poster grid is showing. Paging is cursor based because the
 * MangaDex feed reports `hasMore` rather than a fixed page count.
 */
data class PageCursor(
    val page: Int = 1,
    val hasNext: Boolean = false,
) {
    init {
        require(page >= 1) { "Page numbers are 1-based" }
    }

    val hasPrevious: Boolean get() = page > 1

    /** No-op at the last known page so boundary key presses never fire a request. */
    fun next(): PageCursor = if (hasNext) copy(page = page + 1, hasNext = false) else this

    fun previous(): PageCursor = if (hasPrevious) copy(page = page - 1, hasNext = true) else this

    fun withNext(available: Boolean): PageCursor = copy(hasNext = available)

    companion object {
        val First = PageCursor()
    }
}
