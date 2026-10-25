package com.yomira.feature.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import coil.ImageLoader
import kotlinx.coroutines.flow.distinctUntilChanged

/** Long-strip decode budget; tall webtoon images are down-sampled to this. */
private const val WEBTOON_DECODE_MAX_HEIGHT = 4096

@Composable
fun WebtoonReader(
    state: ReaderUiState,
    imageLoader: ImageLoader,
    jumpTo: Int?,
    onJumpHandled: () -> Unit,
    onPage: (Int) -> Unit,
    onTap: () -> Unit,
    copy: ReaderCopy = ReaderCopy(),
) {
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = boundedPage(state.pageIndex, state.pages.size),
    )
    var zoomedPages by remember { mutableStateOf(emptySet<Int>()) }
    val pageCallback by rememberUpdatedState(onPage)

    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { pageCallback(it) }
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo != null) {
            list.scrollToItem(boundedPage(jumpTo, state.pages.size))
            onJumpHandled()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = list, userScrollEnabled = zoomedPages.isEmpty()) {
        itemsIndexed(
            state.pages,
            key = { _, page -> page.index },
            contentType = { _, _ -> "reader-page" },
        ) { _, page ->
            ReaderPage(
                page = page,
                imageLoader = imageLoader,
                inverted = state.inverted,
                webtoon = true,
                onTap = onTap,
                onZoomed = { zoomed ->
                    zoomedPages = if (zoomed) zoomedPages + page.index else zoomedPages - page.index
                },
                copy = copy,
                mode = state.mode,
                guided = false,
                decodeMaxHeight = WEBTOON_DECODE_MAX_HEIGHT,
            )
        }
    }
}
