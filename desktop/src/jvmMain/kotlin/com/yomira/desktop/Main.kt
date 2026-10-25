package com.yomira.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yomira.desktop.data.DesktopEnvironment
import com.yomira.desktop.state.DesktopAction
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.DesktopScreen
import com.yomira.desktop.state.DesktopShortcuts
import com.yomira.desktop.state.FullscreenController
import com.yomira.desktop.state.WindowMode
import com.yomira.desktop.ui.YomiraDesktopApp
import com.yomira.desktop.ui.YomiraDesktopTheme
import java.awt.Dimension
import org.jetbrains.skia.Image as SkiaImage

/**
 * Entry point of the standalone Windows build.
 *
 * The window opens maximized on a 1600x1000 default canvas and can switch to a
 * true borderless fullscreen with F11. Shortcuts are resolved by the pure
 * [DesktopShortcuts] table so the same behaviour is covered by unit tests.
 */
fun main() = application {
    val environment = remember { DesktopEnvironment.create() }
    val scope = rememberCoroutineScope()
    val state = remember(environment) {
        DesktopAppState(
            catalog = environment.catalog,
            chapters = environment.chapters,
            scope = scope,
        )
    }
    val searchFocusRequester = remember { FocusRequester() }
    var fullscreen by remember { mutableStateOf(FullscreenController()) }
    val windowState = rememberWindowState(
        placement = WindowPlacement.Maximized,
        width = 1600.dp,
        height = 1000.dp,
        position = WindowPosition(Alignment.Center),
    )

    LaunchedEffect(fullscreen.mode) {
        windowState.placement = fullscreen.mode.toPlacement()
    }
    DisposableEffect(Unit) {
        onDispose { environment.close() }
    }

    Window(
        onCloseRequest = {
            environment.close()
            exitApplication()
        },
        state = windowState,
        title = "Yomira",
        icon = windowIcon,
        onPreviewKeyEvent = { event ->
            val action = DesktopShortcuts.resolve(
                event = event,
                isTyping = state.isSearchFocused,
                isFullscreen = fullscreen.isFullscreen,
                hasLayer = state.hasLayer,
            )
            when (action) {
                null -> false
                DesktopAction.TOGGLE_FULLSCREEN -> {
                    fullscreen = fullscreen.toggle()
                    true
                }
                DesktopAction.EXIT_FULLSCREEN -> {
                    fullscreen = fullscreen.exit()
                    true
                }
                DesktopAction.PREVIOUS_PAGE -> {
                    state.previousPage()
                    true
                }
                DesktopAction.NEXT_PAGE -> {
                    state.nextPage()
                    true
                }
                DesktopAction.CLOSE_LAYER -> {
                    state.back()
                    true
                }
                DesktopAction.FOCUS_SEARCH -> {
                    if (state.screen == DesktopScreen.BROWSE) {
                        searchFocusRequester.requestFocus()
                    }
                    true
                }
                // The focused search field owns focus clearing, so let the event
                // continue to it instead of consuming Escape here.
                DesktopAction.BLUR_SEARCH -> false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1024, 700)
        }
        YomiraDesktopTheme {
            YomiraDesktopApp(
                state = state,
                client = environment.images,
                isFullscreen = fullscreen.isFullscreen,
                onToggleFullscreen = { fullscreen = fullscreen.toggle() },
                searchFocusRequester = searchFocusRequester,
            )
        }
    }
}

private fun WindowMode.toPlacement(): WindowPlacement = when (this) {
    WindowMode.WINDOWED -> WindowPlacement.Floating
    WindowMode.MAXIMIZED -> WindowPlacement.Maximized
    WindowMode.FULLSCREEN -> WindowPlacement.Fullscreen
}

/** Loads the PNG logo from the runtime classpath for the native window/taskbar icon. */
private val windowIcon: Painter? by lazy {
    runCatching {
        val stream = object {}.javaClass.getResourceAsStream("/yomira.png") ?: return@runCatching null
        val bytes = stream.use { it.readBytes() }
        BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    }.getOrNull()
}
