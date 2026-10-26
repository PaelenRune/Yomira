package com.yomira.desktop.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F11 / Esc state machine. The window host only applies [WindowMode] to Compose;
 * every decision (what to restore, what counts as fullscreen) lives here so it
 * can be tested without an AWT window.
 */
class FullscreenControllerTest {

    @Test
    fun `starts maximized and enters true fullscreen on the first toggle`() {
        val controller = FullscreenController()
        assertFalse(controller.isFullscreen)
        assertEquals(WindowMode.MAXIMIZED, controller.mode)

        val fullscreen = controller.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertEquals(WindowMode.FULLSCREEN, fullscreen.mode)
        assertEquals("the previous mode is remembered", WindowMode.MAXIMIZED, fullscreen.restoreTo)
    }

    @Test
    fun `leaving fullscreen restores the previous mode`() {
        val fromWindowed = FullscreenController(WindowMode.WINDOWED).toggle()
        assertEquals(WindowMode.FULLSCREEN, fromWindowed.mode)

        val restored = fromWindowed.toggle()
        assertEquals(WindowMode.WINDOWED, restored.mode)
        assertFalse(restored.isFullscreen)
        assertEquals(WindowMode.MAXIMIZED, restored.restoreTo)
    }

    @Test
    fun `escape outside fullscreen is a no-op`() {
        val controller = FullscreenController(WindowMode.MAXIMIZED)
        assertEquals(controller, controller.exit())
    }

    @Test
    fun `double escape never leaves the window stuck in fullscreen`() {
        val controller = FullscreenController(WindowMode.WINDOWED).toggle()
        val first = controller.exit()
        val second = first.exit()
        assertEquals(WindowMode.WINDOWED, first.mode)
        assertEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fullscreen cannot restore into fullscreen`() {
        FullscreenController(WindowMode.FULLSCREEN, restoreTo = WindowMode.FULLSCREEN)
    }
}
