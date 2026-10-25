package com.yomira.desktop.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the reader key contract documented in the README: paged layouts turn
 * pages with the arrows, webtoon scrolls, and manga order flips `←`/`→`.
 */
class DesktopReaderNavigationTest {

    @Test
    fun `manga layout turns forward with left arrow`() {
        val intent = readerIntent(Key.DirectionLeft, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT)
        assertEquals(ReaderIntent.PRE, intent)
        assertEquals(1, stepForArrow(intent, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT))
    }

    @Test
    fun `western layout turns forward with right arrow`() {
        val intent = readerIntent(Key.DirectionRight, DesktopReadingLayout.PAGED_LEFT_TO_RIGHT)
        assertEquals(ReaderIntent.NEXT, intent)
        assertEquals(1, stepForArrow(intent, DesktopReadingLayout.PAGED_LEFT_TO_RIGHT))
    }

    @Test
    fun `double page steps a whole spread`() {
        assertEquals(
            2,
            stepForArrow(ReaderIntent.NEXT, DesktopReadingLayout.DOUBLE_PAGE),
        )
        assertEquals(
            -2,
            stepForArrow(ReaderIntent.PRE, DesktopReadingLayout.DOUBLE_PAGE),
        )
    }

    @Test
    fun `webtoon maps vertical keys to scrolling`() {
        assertEquals(ReaderIntent.SCROLL_DOWN, readerIntent(Key.DirectionDown, DesktopReadingLayout.WEBTOON))
        assertEquals(ReaderIntent.SCROLL_UP, readerIntent(Key.PageUp, DesktopReadingLayout.WEBTOON))
        assertEquals(0, stepForArrow(ReaderIntent.SCROLL_DOWN, DesktopReadingLayout.WEBTOON))
    }

    @Test
    fun `home and end jump to bounds`() {
        assertEquals(ReaderIntent.FIRST, readerIntent(Key.MoveHome, DesktopReadingLayout.PAGED_LEFT_TO_RIGHT))
        assertEquals(ReaderIntent.LAST, readerIntent(Key.MoveEnd, DesktopReadingLayout.PAGED_LEFT_TO_RIGHT))
    }

    @Test
    fun `unrelated keys do nothing`() {
        assertEquals(ReaderIntent.NONE, readerIntent(Key.A, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT))
        assertEquals(0, stepForArrow(ReaderIntent.NONE, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT))
    }
}
