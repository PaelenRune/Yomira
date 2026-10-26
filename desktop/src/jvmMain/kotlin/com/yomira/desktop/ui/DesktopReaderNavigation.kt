package com.yomira.desktop.ui

import androidx.compose.ui.input.key.Key

/** How a chapter is laid out on a wide desktop canvas. */
enum class DesktopReadingLayout {
    /** Continuous vertical strip; the default for webtoons/manhwa. */
    WEBTOON,
    /** Single page, forward to the left (Japanese manga). */
    PAGED_RIGHT_TO_LEFT,
    /** Single page, forward to the right (western comics). */
    PAGED_LEFT_TO_RIGHT,
    /** Two pages per spread, forward to the left (manga). */
    DOUBLE_PAGE,
}

/** A reader command, independent of the arrow that produced it. */
enum class ReaderIntent { NONE, PRE, NEXT, SCROLL_UP, SCROLL_DOWN, FIRST, LAST }

/**
 * Maps a key to a reader command for a given layout.
 *
 * `PRE` / `NEXT` are *visual* directions: `PRE` is the left arrow, `NEXT` the
 * right arrow, regardless of reading direction. [stepForArrow] turns them into
 * an index delta, so an RTL pager advances with the left arrow without every
 * call site re-deriving the convention.
 */
fun readerIntent(key: Key, layout: DesktopReadingLayout): ReaderIntent = when (layout) {
    DesktopReadingLayout.WEBTOON -> when (key) {
        Key.DirectionDown, Key.PageDown, Key.Spacebar -> ReaderIntent.SCROLL_DOWN
        Key.DirectionUp, Key.PageUp -> ReaderIntent.SCROLL_UP
        Key.MoveHome -> ReaderIntent.FIRST
        Key.MoveEnd -> ReaderIntent.LAST
        else -> ReaderIntent.NONE
    }
    else -> when (key) {
        Key.DirectionLeft -> ReaderIntent.PRE
        Key.DirectionRight -> ReaderIntent.NEXT
        Key.Spacebar, Key.PageDown -> ReaderIntent.NEXT
        Key.PageUp -> ReaderIntent.PRE
        Key.MoveHome -> ReaderIntent.FIRST
        Key.MoveEnd -> ReaderIntent.LAST
        else -> ReaderIntent.NONE
    }
}

/**
 * Index delta of [intent] in [layout]. `0` means the reader should handle the
 * intent itself (scroll, jump to a bound) or ignore it.
 */
fun stepForArrow(intent: ReaderIntent, layout: DesktopReadingLayout): Int = when (layout) {
    DesktopReadingLayout.WEBTOON -> 0
    DesktopReadingLayout.DOUBLE_PAGE -> when (intent) {
        ReaderIntent.NEXT -> 2
        ReaderIntent.PRE -> -2
        else -> 0
    }
    DesktopReadingLayout.PAGED_RIGHT_TO_LEFT -> when (intent) {
        ReaderIntent.PRE -> 1
        ReaderIntent.NEXT -> -1
        else -> 0
    }
    DesktopReadingLayout.PAGED_LEFT_TO_RIGHT -> when (intent) {
        ReaderIntent.PRE -> -1
        ReaderIntent.NEXT -> 1
        else -> 0
    }
}

/** Clamps a page target into `0 until pageCount` without throwing on an empty chapter. */
fun clampReaderPage(page: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return page.coerceIn(0, pageCount - 1)
}
