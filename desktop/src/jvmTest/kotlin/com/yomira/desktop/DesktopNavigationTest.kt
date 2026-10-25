package com.yomira.desktop

import androidx.compose.ui.input.key.Key
import com.yomira.desktop.state.DesktopAction
import com.yomira.desktop.state.DesktopShortcuts
import com.yomira.desktop.state.PageCursor
import com.yomira.desktop.state.PosterGrid
import com.yomira.desktop.ui.DesktopReadingLayout
import com.yomira.desktop.ui.ReaderIntent
import com.yomira.desktop.ui.readerIntent
import com.yomira.desktop.ui.stepForArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure shell contract of the desktop target: the responsive poster columns, the
 * cursor pager and the keyboard tables. None of this needs a window server, so
 * `:desktop:test` runs them headlessly.
 */
class DesktopNavigationTest {

    @Test
    fun `poster columns follow the common Windows widths`() {
        assertEquals(2, PosterGrid.columnsForWidth(0f))
        assertEquals(2, PosterGrid.columnsForWidth(600f))
        assertEquals(3, PosterGrid.columnsForWidth(1_024f))
        assertEquals(4, PosterGrid.columnsForWidth(1_200f))
        assertEquals(5, PosterGrid.columnsForWidth(1_500f))
        assertEquals(6, PosterGrid.columnsForWidth(1_920f))
        assertEquals(7, PosterGrid.columnsForWidth(2_560f))
        assertEquals(8, PosterGrid.columnsForWidth(3_440f))
        assertEquals(8, PosterGrid.columnsForWidth(3_840f))
    }

    @Test
    fun `poster columns stay inside the supported range`() {
        assertTrue(PosterGrid.columnsForWidth(Float.NaN) in PosterGrid.MIN_COLUMNS..PosterGrid.MAX_COLUMNS)
        assertTrue(
            PosterGrid.columnsForWidth(20_000f) in PosterGrid.MIN_COLUMNS..PosterGrid.MAX_COLUMNS,
        )
    }

    @Test
    fun `cursor paging is 1-based and never leaves the feed`() {
        val first = PageCursor.First
        assertEquals(1, first.page)
        assertFalse(first.hasPrevious)
        assertFalse(first.hasNext)
        assertEquals(first, first.next())
        assertEquals(first, first.previous())

        val second = first.withNext(true).next()
        assertEquals(2, second.page)
        assertTrue(second.hasPrevious)
        assertEquals(first.page, second.previous().page)
    }

    @Test
    fun `f11 toggles fullscreen from every state`() {
        assertEquals(DesktopAction.TOGGLE_FULLSCREEN, DesktopShortcuts.resolve(Key.F11))
        assertEquals(
            DesktopAction.TOGGLE_FULLSCREEN,
            DesktopShortcuts.resolve(Key.F11, isTyping = true, isFullscreen = true, hasLayer = true),
        )
    }

    @Test
    fun `escape unwinds fullscreen then search then layers`() {
        assertEquals(
            DesktopAction.EXIT_FULLSCREEN,
            DesktopShortcuts.resolve(Key.Escape, isTyping = true, isFullscreen = true, hasLayer = true),
        )
        assertEquals(
            DesktopAction.BLUR_SEARCH,
            DesktopShortcuts.resolve(Key.Escape, isTyping = true),
        )
        assertEquals(
            DesktopAction.CLOSE_LAYER,
            DesktopShortcuts.resolve(Key.Escape, hasLayer = true),
        )
        assertNull(DesktopShortcuts.resolve(Key.Escape))
    }

    @Test
    fun `ctrl f focuses search while arrows page the grid`() {
        assertEquals(
            DesktopAction.FOCUS_SEARCH,
            DesktopShortcuts.resolve(Key.F, ctrlPressed = true),
        )
        assertEquals(DesktopAction.PREVIOUS_PAGE, DesktopShortcuts.resolve(Key.DirectionLeft))
        assertEquals(DesktopAction.PREVIOUS_PAGE, DesktopShortcuts.resolve(Key.MoveHome))
        assertEquals(DesktopAction.PREVIOUS_PAGE, DesktopShortcuts.resolve(Key.PageUp))
        assertEquals(DesktopAction.NEXT_PAGE, DesktopShortcuts.resolve(Key.DirectionRight))
        assertEquals(DesktopAction.NEXT_PAGE, DesktopShortcuts.resolve(Key.Spacebar))
        assertEquals(DesktopAction.NEXT_PAGE, DesktopShortcuts.resolve(Key.PageDown))
    }

    @Test
    fun `typing and open layers own the arrow keys`() {
        assertNull(DesktopShortcuts.resolve(Key.DirectionRight, isTyping = true))
        assertNull(DesktopShortcuts.resolve(Key.Spacebar, isTyping = true))
        assertNull(DesktopShortcuts.resolve(Key.DirectionRight, hasLayer = true))
        assertNull(DesktopShortcuts.resolve(Key.PageDown, hasLayer = true))
        assertNull(DesktopShortcuts.resolve(Key.A))
    }

    @Test
    fun `manga arrows turn forward towards the left`() {
        assertEquals(ReaderIntent.PRE, readerIntent(Key.DirectionLeft, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT))
        assertEquals(ReaderIntent.NEXT, readerIntent(Key.DirectionRight, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT))
        assertEquals(1, stepForArrow(ReaderIntent.PRE, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT))
        assertEquals(-1, stepForArrow(ReaderIntent.NEXT, DesktopReadingLayout.PAGED_RIGHT_TO_LEFT))
    }

    @Test
    fun `western arrows follow the visual direction`() {
        assertEquals(-1, stepForArrow(ReaderIntent.PRE, DesktopReadingLayout.PAGED_LEFT_TO_RIGHT))
        assertEquals(1, stepForArrow(ReaderIntent.NEXT, DesktopReadingLayout.PAGED_LEFT_TO_RIGHT))
    }

    @Test
    fun `spread mode steps two pages and webtoon scrolls`() {
        assertEquals(2, stepForArrow(ReaderIntent.NEXT, DesktopReadingLayout.DOUBLE_PAGE))
        assertEquals(
            ReaderIntent.SCROLL_DOWN,
            readerIntent(Key.DirectionDown, DesktopReadingLayout.WEBTOON),
        )
        assertEquals(
            ReaderIntent.SCROLL_UP,
            readerIntent(Key.PageUp, DesktopReadingLayout.WEBTOON),
        )
        assertEquals(ReaderIntent.FIRST, readerIntent(Key.MoveHome, DesktopReadingLayout.WEBTOON))
        assertEquals(ReaderIntent.LAST, readerIntent(Key.MoveEnd, DesktopReadingLayout.WEBTOON))
    }
}
