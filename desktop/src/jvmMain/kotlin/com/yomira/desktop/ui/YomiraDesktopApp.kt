package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.yomira.desktop.data.openInBrowser
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.DesktopScreen
import com.yomira.desktop.state.PosterGrid
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/** Width at which the icon rail earns its place next to the masthead. */
private val RailBreakpoint = 1_180.dp

/** Above this width the rail shows labels instead of icons only. */
private val RailExpandedBreakpoint = 1_680.dp

/**
 * Root shell of the desktop target.
 *
 * The shell reads like the Cstation reference: a slim navigation rail on the
 * left, the masthead across the top, then the poster wall. The reader takes the
 * whole canvas so the artwork is the only thing on screen.
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
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) { state.start() }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_200)
            notice = null
        }
    }

    fun openBrowser(url: String?) {
        if (url == null) return
        if (!openInBrowser(url)) {
            notice = "Tarayıcı açılamadı. Adresi kopyalayıp elle açabilirsin."
        }
    }

    fun openChapter(chapter: DesktopChapter) {
        if (chapter.isReadable) {
            state.openChapter(chapter)
        } else {
            openBrowser(chapter.externalUrl)
        }
    }

    fun focusSearch() {
        searchFocusRequester.requestFocus()
    }

    OledBackdrop(modifier.fillMaxSize()) {
        if (state.screen == DesktopScreen.READER) {
            DesktopReaderScreen(
                state = state,
                client = client,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val showRail = maxWidth >= RailBreakpoint
                val expandedRail = maxWidth >= RailExpandedBreakpoint
                Row(Modifier.fillMaxSize()) {
                    if (showRail) {
                        YomiraRail(
                            state = state,
                            isFullscreen = isFullscreen,
                            expanded = expandedRail,
                            onToggleFullscreen = onToggleFullscreen,
                            onFocusSearch = ::focusSearch,
                        )
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Hairline.copy(alpha = 0.55f)),
                        )
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(
                            state = state,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = onToggleFullscreen,
                            searchFocusRequester = searchFocusRequester,
                        )
                        BoxWithConstraints(Modifier.weight(1f)) {
                            // The shell gutter is excluded from the breakpoint math so the
                            // poster columns match the visible content width exactly.
                            val columns = PosterGrid.columnsForWidth(maxWidth.value - 2 * ShellPadding.value)
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
                                    onOpenChapter = ::openChapter,
                                    onOpenBrowser = ::openBrowser,
                                    related = state.items
                                        .filter { it.id != state.selectedItem?.id }
                                        .take(8),
                                )

                                DesktopScreen.READER -> Unit
                            }
                        }
                    }
                }
            }
        }

        notice?.let { message ->
            Box(
                Modifier.fillMaxSize().padding(bottom = 84.dp),
                contentAlignment = Alignment.BottomCenter,
            ) { NoticePill(message) }
        }
    }
}
