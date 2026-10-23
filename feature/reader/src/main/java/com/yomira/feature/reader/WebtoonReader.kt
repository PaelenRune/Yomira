package com.yomira.feature.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import coil.ImageLoader
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun WebtoonReader(state: ReaderUiState, imageLoader: ImageLoader, jumpTo: Int?, onJumpHandled: () -> Unit,
    onPage: (Int) -> Unit, onTap: () -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex)
    var zoomedPages by remember { mutableStateOf(emptySet<Int>()) }
    val pageCallback by rememberUpdatedState(onPage)
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { pageCallback(it) }
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo != null) { list.scrollToItem(boundedPage(jumpTo, state.pages.size)); onJumpHandled() }
    }
    LazyColumn(Modifier.fillMaxSize(), state = list, userScrollEnabled = zoomedPages.isEmpty()) {
        itemsIndexed(state.pages, key = { _, page -> page.index }, contentType = { _, _ -> "reader-page" }) { _, page ->
            ReaderPage(page, imageLoader, state.inverted, webtoon = true, onTap = onTap,
                onZoomed = { zoomedPages = if (it) zoomedPages + page.index else zoomedPages - page.index })
        }
    }
}
