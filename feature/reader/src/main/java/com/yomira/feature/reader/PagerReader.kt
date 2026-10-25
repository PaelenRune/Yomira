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
import kotlinx.coroutines.launch

/**
 * Paged reader.
 *
 * [slots] is the planned reading surface: portrait pages stay singles, recognised
 * two-page drawings are stitched into one wide scene ([SpreadSlot.Stitched]), and a
 * landscape page keeps its own wide slot. The pager counts slots, while progress is
 * always reported as the first page of the visible slot, so saved positions survive
 * switching between single- and two-page layouts.
 */
@Composable
fun PagerReader(
    state: ReaderUiState,
    imageLoader: ImageLoader,
    jumpTo: Int?,
    onJumpHandled: () -> Unit,
    onPage: (Int) -> Unit,
    onTap: () -> Unit,
    copy: ReaderCopy = ReaderCopy(),
    slots: List<SpreadSlot> = state.pages.indices.map { SpreadSlot.Single(it) },
    guided: Boolean = false,
    onPageGeometry: (index: Int, width: Int, height: Int) -> Unit = { _, _, _ -> },
) {
    val pageCount = state.pages.size
    val slotCount = slots.size
    fun slotForPage(page: Int): Int {
        if (slotCount == 0) return 0
        val bounded = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val exact = slots.indexOfFirst { bounded in it.firstPage..it.lastPage }
        return if (exact >= 0) exact else 0
    }

    val pager = rememberPagerState(initialPage = slotForPage(state.pageIndex)) { slotCount }
    val scope = rememberCoroutineScope()
    var zoomedPages by remember { mutableStateOf(emptySet<Int>()) }
    val callback by rememberUpdatedState(onPage)
    val navigate: (Int) -> Unit = { step ->
        if (step != 0 && pageCount > 0) {
            val target = (state.pageIndex + step).coerceIn(0, pageCount - 1)
            scope.launch { pager.animateScrollToPage(slotForPage(target)) }
        }
    }
    LaunchedEffect(pager, slotCount) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { slotIndex ->
            slots.getOrNull(slotIndex)?.let { callback(it.firstPage) }
        }
    }
    LaunchedEffect(jumpTo) {
        if (jumpTo != null) {
            pager.scrollToPage(slotForPage(jumpTo))
            onJumpHandled()
        }
    }
    // Explicit LTR ambient makes the chosen reading direction independent of the device language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize(),
            reverseLayout = state.mode.isRightToLeft(),
            userScrollEnabled = zoomedPages.isEmpty(),
            beyondViewportPageCount = 0,
            key = { slotIndex -> slots.getOrNull(slotIndex)?.firstPage ?: slotIndex },
        ) { slotIndex ->
            val slot = slots.getOrNull(slotIndex) ?: return@HorizontalPager
            when (slot) {
                is SpreadSlot.Single -> {
                    val page = state.pages.getOrNull(slot.page) ?: return@HorizontalPager
                    ReaderPage(
                        page = page,
                        imageLoader = imageLoader,
                        inverted = state.inverted,
                        webtoon = false,
                        onTap = onTap,
                        onZoomed = { zoomed ->
                            zoomedPages = if (zoomed) zoomedPages + page.index else zoomedPages - page.index
                        },
                        copy = copy,
                        mode = state.mode,
                        guided = guided,
                        onNavigate = navigate,
                        onGeometry = { width, height -> onPageGeometry(page.index, width, height) },
                    )
                }
                is SpreadSlot.Stitched -> {
                    val first = state.pages.getOrNull(slot.leftPage)
                    val second = state.pages.getOrNull(slot.rightPage)
                    if (first != null && second != null) {
                        // Manga reads right to left: the first file page sits on
                        // the right half of the stitched scene.
                        val left = if (state.mode.isRightToLeft()) second else first
                        val right = if (state.mode.isRightToLeft()) first else second
                        SpreadScene(
                            leftPage = left,
                            rightPage = right,
                            imageLoader = imageLoader,
                            inverted = state.inverted,
                            mode = state.mode,
                            onTap = onTap,
                            onZoomed = { zoomed ->
                                val pages = setOf(left.index, right.index)
                                zoomedPages = if (zoomed) zoomedPages + pages else zoomedPages - pages
                            },
                            copy = copy,
                        )
                    }
                }
            }
        }
    }
}
