package com.yomira.desktop.state

/** Window modes the desktop shell can occupy. */
enum class WindowMode { WINDOWED, MAXIMIZED, FULLSCREEN }

/**
 * F11 toggles a true borderless fullscreen; leaving fullscreen restores the mode
 * the window had before (usually maximized). Pure state, applied to Compose's
 * [androidx.compose.ui.window.WindowState] by the window host.
 */
data class FullscreenController(
    val mode: WindowMode = WindowMode.MAXIMIZED,
    val restoreTo: WindowMode = WindowMode.MAXIMIZED,
) {
    init {
        require(mode != WindowMode.FULLSCREEN || restoreTo != WindowMode.FULLSCREEN) {
            "Fullscreen cannot restore into fullscreen"
        }
    }

    val isFullscreen: Boolean get() = mode == WindowMode.FULLSCREEN

    fun toggle(): FullscreenController =
        if (isFullscreen) exit() else FullscreenController(WindowMode.FULLSCREEN, restoreTo = mode)

    fun exit(): FullscreenController =
        if (isFullscreen) copy(mode = restoreTo, restoreTo = WindowMode.MAXIMIZED) else this
}
