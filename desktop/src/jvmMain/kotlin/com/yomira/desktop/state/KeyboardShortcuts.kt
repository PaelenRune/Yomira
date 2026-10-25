package com.yomira.desktop.state

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** What a key press asks the desktop shell to do. */
enum class DesktopAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    TOGGLE_FULLSCREEN,
    EXIT_FULLSCREEN,
    CLOSE_LAYER,
    BLUR_SEARCH,
    FOCUS_SEARCH,
}

/**
 * Single source of truth for desktop shortcuts, kept free of UI side effects.
 *
 * - `F11` toggles fullscreen, always.
 * - `Esc` leaves fullscreen first, then search focus, then the open detail/reader layer.
 * - `←` / `→` / `Home` / `End` / `PageUp` / `PageDown` page the poster grid.
 * - `Ctrl+F` focuses the search field.
 *
 * Keys typed into the search field never paginate, otherwise the caret would
 * stop working. `F11` and fullscreen `Esc` stay global. Inside the reader the
 * detail/reader layer owns its arrow keys, so grid paging stays disabled there.
 */
object DesktopShortcuts {
    fun resolve(
        key: Key,
        ctrlPressed: Boolean = false,
        isTyping: Boolean = false,
        isFullscreen: Boolean = false,
        hasLayer: Boolean = false,
    ): DesktopAction? {
        if (key == Key.F11) return DesktopAction.TOGGLE_FULLSCREEN
        if (key == Key.Escape) {
            return when {
                isFullscreen -> DesktopAction.EXIT_FULLSCREEN
                isTyping -> DesktopAction.BLUR_SEARCH
                hasLayer -> DesktopAction.CLOSE_LAYER
                else -> null
            }
        }
        if (ctrlPressed && key == Key.F) return DesktopAction.FOCUS_SEARCH
        // Text editing owns the arrow keys and Space while the search field is
        // focused, and the reader owns them while a chapter is open.
        if (isTyping || hasLayer) return null
        return when (key) {
            Key.DirectionLeft, Key.MoveHome, Key.PageUp -> DesktopAction.PREVIOUS_PAGE
            Key.DirectionRight, Key.Spacebar, Key.PageDown -> DesktopAction.NEXT_PAGE
            else -> null
        }
    }

    fun resolve(
        event: KeyEvent,
        isTyping: Boolean = false,
        isFullscreen: Boolean = false,
        hasLayer: Boolean = false,
    ): DesktopAction? {
        if (event.type != KeyEventType.KeyDown) return null
        return resolve(
            key = event.key,
            ctrlPressed = event.isCtrlPressed,
            isTyping = isTyping,
            isFullscreen = isFullscreen,
            hasLayer = hasLayer,
        )
    }
}
