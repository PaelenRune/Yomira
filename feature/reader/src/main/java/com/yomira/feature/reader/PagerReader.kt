package com.yomira.feature.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil.ImageLoader
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PagerReader(state: ReaderUiState, imageLoader: ImageLoader, jumpTo: Int?, onJumpHandled: () -> Unit,
    onPage: (Int) -> Unit, onTap: () -> Unit, copy: ReaderCopy = ReaderCopy()) {
    val pager = rememberPagerState(initialPage = state.pageIndex) { state.pages.size }
    var zoomedPages by remember { mutableStateOf(emptySet<Int>()) }
    val callback by rememberUpdatedState(onPage)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { callback(it) } }
    LaunchedEffect(jumpTo) {
        if (jumpTo != null) { pager.scrollToPage(boundedPage(jumpTo, state.pages.size)); onJumpHandled() }
    }
    // Explicit LTR ambient makes the chosen reading direction independent of the device language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = state.mode.isRightToLeft(),
            userScrollEnabled = zoomedPages.isEmpty(), beyondViewportPageCount = 0, key = { state.pages[it].index }) { index ->
            ReaderPage(state.pages[index], imageLoader, state.inverted, webtoon = false, onTap = onTap,
                onZoomed = { zoomedPages = if (it) zoomedPages + index else zoomedPages - index }, copy = copy)
        }
    }
}
