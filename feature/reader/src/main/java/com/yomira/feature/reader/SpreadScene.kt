package com.yomira.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.ImageLoader
import com.yomira.core.model.ReadingMode
import com.yomira.domain.extension.SourcePage

/**
 * One stitched double-page scene: the two halves share the viewport width and
 * keep their individual pinch-zoom, so either side can still be inspected.
 *
 * [leftPage] / [rightPage] are already placed physically; the caller resolves
 * manga's right-to-left order before calling this composable.
 */
@Composable
internal fun SpreadScene(
    leftPage: SourcePage,
    rightPage: SourcePage,
    imageLoader: ImageLoader,
    inverted: Boolean,
    mode: ReadingMode,
    onTap: () -> Unit,
    onZoomed: (Boolean) -> Unit,
    copy: ReaderCopy,
) {
    Row(Modifier.fillMaxSize()) {
        SpreadHalf(
            page = leftPage,
            imageLoader = imageLoader,
            inverted = inverted,
            mode = mode,
            onTap = onTap,
            onZoomed = onZoomed,
            copy = copy,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SpreadHalf(
            page = rightPage,
            imageLoader = imageLoader,
            inverted = inverted,
            mode = mode,
            onTap = onTap,
            onZoomed = onZoomed,
            copy = copy,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SpreadHalf(
    page: SourcePage,
    imageLoader: ImageLoader,
    inverted: Boolean,
    mode: ReadingMode,
    onTap: () -> Unit,
    onZoomed: (Boolean) -> Unit,
    copy: ReaderCopy,
    modifier: Modifier,
) {
    Box(modifier) {
        ReaderPage(
            page = page,
            imageLoader = imageLoader,
            inverted = inverted,
            webtoon = false,
            onTap = onTap,
            onZoomed = onZoomed,
            copy = copy,
            mode = mode,
        )
    }
}
