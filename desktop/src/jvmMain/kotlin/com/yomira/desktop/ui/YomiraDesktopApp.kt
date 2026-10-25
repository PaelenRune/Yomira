package com.yomira.desktop.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.DesktopScreen
import com.yomira.desktop.state.PosterGrid
import okhttp3.OkHttpClient

/**
 * Root shell of the desktop target.
 *
 * The masthead stays mounted above every surface, so search and the fullscreen
 * toggle are always reachable. The poster wall is the only screen with the
 * pager footer; detail and reader own the rest of the canvas.
 */
@Composable
fun YomiraDesktopApp(
    state: DesktopAppState,
    client: OkHttpClient,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    searchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state) { state.start() }

    OledBackdrop(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                searchFocusRequester = searchFocusRequester,
            )
            BoxWithConstraints(Modifier.weight(1f)) {
                // The shell gutter is excluded from the breakpoint math so the
                // poster columns match the visible content width exactly.
                val columns = PosterGrid.columnsForWidth(
                    maxWidth.value - 2 * ShellPadding.value,
                )
                when (state.screen) {
                    DesktopScreen.BROWSE -> Column(Modifier.fillMaxSize()) {
                        BrowsePane(
                            state = state,
                            columns = columns,
                            client = client,
                            onSelect = state::openDetail,
                            modifier = Modifier.weight(1f),
                        )
                        PagerFooter(state)
                    }

                    DesktopScreen.DETAIL -> DesktopDetailScreen(
                        state = state,
                        client = client,
                    )

                    DesktopScreen.READER -> DesktopReaderScreen(
                        state = state,
                        client = client,
                    )
                }
            }
        }
    }
}
