package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.model.DesktopPage
import com.yomira.desktop.state.DesktopAppState
import kotlin.math.ceil
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Immersive desktop reader.
 *
 * Webtoon is the default: one continuous [LazyColumn], so the mouse wheel glides
 * the strip and only visible images are downloaded. Paged layouts use a
 * [HorizontalPager] that follows Japanese (right-to-left) or western
 * (left-to-right) order, and a two-page spread for manga.
 *
 * Chrome is reduced to two floating capsules â€” chapter/layout controls on top,
 * progress and jump-to-top at the bottom â€” so the art keeps the canvas. All key
 * handling goes through [readerIntent] / [stepForArrow], the pure table covered
 * by `DesktopReaderNavigationTest`.
 */
@Composable
fun DesktopReaderScreen(
    state: DesktopAppState,
    client: OkHttpClient,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.reader
    if (session == null) {
        state.closeReader()
        return
    }

    var layout by remember(session.item.id) { mutableStateOf(DesktopReadingLayout.WEBTOON) }
    val listState = rememberLazyListState()
    val pagesPerSpread = if (layout == DesktopReadingLayout.DOUBLE_PAGE) 2 else 1
    val pagerCount = ceil(session.pages.size / pagesPerSpread.toFloat()).toInt().coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pagerCount })
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(session.chapter.id, session.pages.size) {
        listState.scrollToItem(0)
        pagerState.scrollToPage(0)
        state.updateReaderPage(0)
        focusRequester.requestFocus()
    }
    LaunchedEffect(listState, layout) {
        if (layout == DesktopReadingLayout.WEBTOON) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { state.updateReaderPage(it) }
        }
    }
    LaunchedEffect(pagerState, layout) {
        if (layout != DesktopReadingLayout.WEBTOON) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page -> state.updateReaderPage(page * pagesPerSpread) }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(YomiraColors.ReaderCanvas)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val intent = readerIntent(event.key, layout)
                when (intent) {
                    ReaderIntent.NONE -> false
                    ReaderIntent.SCROLL_DOWN -> {
                        if (layout == DesktopReadingLayout.WEBTOON) {
                            scope.launch { listState.animateScrollBy(900f) }
                            true
                        } else false
                    }
                    ReaderIntent.SCROLL_UP -> {
                        if (layout == DesktopReadingLayout.WEBTOON) {
                            scope.launch { listState.animateScrollBy(-900f) }
                            true
                        } else false
                    }
                    ReaderIntent.FIRST -> {
                        if (layout == DesktopReadingLayout.WEBTOON) {
                            scope.launch { listState.animateScrollToItem(0) }
                        } else {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }
                        true
                    }
                    ReaderIntent.LAST -> {
                        val last = session.pages.lastIndex.coerceAtLeast(0)
                        if (layout == DesktopReadingLayout.WEBTOON) {
                            scope.launch { listState.animateScrollToItem(last) }
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerCount - 1) }
                        }
                        true
                    }
                    ReaderIntent.PRE, ReaderIntent.NEXT -> {
                        if (layout == DesktopReadingLayout.WEBTOON) {
                            false
                        } else {
                            val delta = stepForArrow(intent, layout)
                            val currentPage = pagerState.currentPage * pagesPerSpread
                            val target = clampReaderPage(currentPage + delta, session.pages.size)
                            scope.launch { pagerState.animateScrollToPage(target / pagesPerSpread) }
                            true
                        }
                    }
                }
            },
    ) {
        when {
            session.loading -> ReaderStatus(session.chapter.displayLabel, DesktopCopy.PAGES_LOADING, loading = true)
            session.error != null -> ReaderStatus(
                title = session.chapter.displayLabel,
                message = session.error ?: DesktopCopy.PAGES_FAILED,
                loading = false,
                onRetry = state::retryPages,
            )
            session.pages.isEmpty() -> ReaderStatus(
                title = session.chapter.displayLabel,
                message = DesktopCopy.PAGES_FAILED,
                loading = false,
                onRetry = state::retryPages,
            )
            layout == DesktopReadingLayout.WEBTOON -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 104.dp, bottom = 108.dp),
            ) {
                items(session.pages, key = { it.url }) { page ->
                    WebtoonPage(page, client)
                }
            }
            else -> {
                val reverse = layout == DesktopReadingLayout.PAGED_RIGHT_TO_LEFT ||
                    layout == DesktopReadingLayout.DOUBLE_PAGE
                HorizontalPager(
                    state = pagerState,
                    reverseLayout = reverse,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 92.dp, bottom = 96.dp),
                    pageSpacing = 12.dp,
                ) { spreadIndex ->
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val indexes = if (pagesPerSpread == 2) {
                            listOf(spreadIndex * 2, spreadIndex * 2 + 1)
                        } else {
                            listOf(spreadIndex)
                        }
                        val ordered = if (reverse) indexes.reversed() else indexes
                        ordered.forEach { index ->
                            session.pages.getOrNull(index)?.let { page ->
                                PagedPage(page, client, Modifier.weight(1f))
                            }
                        }
                        if (ordered.size < pagesPerSpread) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Top scrim keeps the floating HUD legible over bright, tall webtoon art.
        Box(
            Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(
                    0f to YomiraColors.ReaderCanvas.copy(alpha = 0.95f),
                    0.55f to YomiraColors.ReaderCanvas.copy(alpha = 0.45f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter).background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to YomiraColors.ReaderCanvas.copy(alpha = 0.42f),
                    1f to YomiraColors.ReaderCanvas.copy(alpha = 0.94f),
                ),
            ),
        )

        ReaderTopCapsule(
            state = state,
            layout = layout,
            isFullscreen = isFullscreen,
            onLayoutChange = { next ->
                layout = next
                val per = if (next == DesktopReadingLayout.DOUBLE_PAGE) 2 else 1
                val page = state.readerPage
                scope.launch {
                    listState.scrollToItem(page.coerceAtLeast(0))
                    pagerState.scrollToPage((page / per).coerceAtLeast(0))
                }
            },
            onClose = state::closeReader,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 18.dp, vertical = 16.dp),
        )

        if (session.pages.isNotEmpty()) {
            ReaderProgressCapsule(
                page = (state.readerPage + 1).coerceAtMost(session.pages.size),
                total = session.pages.size,
                onJumpTop = {
                    when (layout) {
                        DesktopReadingLayout.WEBTOON -> scope.launch { listState.animateScrollToItem(0) }
                        else -> scope.launch { pagerState.animateScrollToPage(0) }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 18.dp),
            )
        }
        ReaderHint(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 26.dp))
    }
}

@Composable
private fun ReaderTopCapsule(
    state: DesktopAppState,
    layout: DesktopReadingLayout,
    isFullscreen: Boolean,
    onLayoutChange: (DesktopReadingLayout) -> Unit,
    onClose: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.reader ?: return
    val previous = state.adjacentChapter(-1)
    val next = state.adjacentChapter(1)
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 900.dp
        val roomy = maxWidth >= 1_180.dp

        Column(
            Modifier.widthIn(max = 1_320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .background(YomiraColors.OledRaised.copy(alpha = 0.88f))
                    .border(1.dp, YomiraColors.OutlineSoft.copy(alpha = 0.9f), shape)
                    .padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconBubble(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = DesktopCopy.BACK,
                    onClick = onClose,
                    size = 40.dp,
                    background = Color.Transparent,
                )
                Box(Modifier.size(width = 1.dp, height = 24.dp).background(Hairline))
                Column(
                    Modifier
                        .then(if (wide) Modifier else Modifier.weight(1f))
                        .widthIn(max = if (roomy) 320.dp else 220.dp),
                ) {
                    Text(
                        session.chapter.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = YomiraColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        session.item.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = YomiraColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (wide) {
                    Spacer(Modifier.weight(1f))
                    ReaderModeChips(layout, onLayoutChange)
                    Spacer(Modifier.weight(1f))
                }
                if (session.pages.isNotEmpty()) {
                    ReaderPageCounter(state.readerPage + 1, session.pages.size)
                }
                if (wide) {
                    IconBubble(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = DesktopCopy.CHAPTER_PREVIOUS,
                        onClick = { state.openAdjacentChapter(-1) },
                        enabled = previous != null,
                        size = 36.dp,
                        background = Color.Transparent,
                    )
                    IconBubble(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = DesktopCopy.CHAPTER_NEXT,
                        onClick = { state.openAdjacentChapter(1) },
                        enabled = next != null,
                        size = 36.dp,
                        background = Color.Transparent,
                    )
                }
                IconBubble(
                    icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullscreen) DesktopCopy.EXIT_FULLSCREEN else DesktopCopy.FULLSCREEN,
                    onClick = onToggleFullscreen,
                    size = 36.dp,
                    background = Color.Transparent,
                    tint = if (isFullscreen) YomiraColors.Lavender else YomiraColors.TextPrimary,
                )
            }

            // Narrow canvases cannot carry the mode row inline; it becomes a
            // second capsule instead of squeezing the labels into slivers.
            if (!wide) {
                Box(
                    Modifier
                        .clip(shape)
                        .background(YomiraColors.OledRaised.copy(alpha = 0.88f))
                        .border(1.dp, YomiraColors.OutlineSoft.copy(alpha = 0.9f), shape)
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    ReaderModeChips(layout, onLayoutChange)
                }
            }
        }
    }
}

@Composable
private fun ReaderModeChips(layout: DesktopReadingLayout, onLayoutChange: (DesktopReadingLayout) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ModeChip("Webtoon", layout == DesktopReadingLayout.WEBTOON) {
            onLayoutChange(DesktopReadingLayout.WEBTOON)
        }
        ModeChip("Manga", layout == DesktopReadingLayout.PAGED_RIGHT_TO_LEFT) {
            onLayoutChange(DesktopReadingLayout.PAGED_RIGHT_TO_LEFT)
        }
        ModeChip("Comic", layout == DesktopReadingLayout.PAGED_LEFT_TO_RIGHT) {
            onLayoutChange(DesktopReadingLayout.PAGED_LEFT_TO_RIGHT)
        }
        ModeChip("Çift", layout == DesktopReadingLayout.DOUBLE_PAGE) {
            onLayoutChange(DesktopReadingLayout.DOUBLE_PAGE)
        }
    }
}

@Composable
private fun ReaderPageCounter(page: Int, total: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(YomiraColors.Surface.copy(alpha = 0.72f))
            .border(1.dp, YomiraColors.OutlineSoft.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        NumericHud(page.toString().padStart(3, '0'))
        Text("/", style = NumericStyle.copy(fontSize = 11.sp), color = YomiraColors.OutlineSoft)
        NumericHud(total.toString().padStart(3, '0'), color = YomiraColors.TextMuted)
    }
}

@Composable
private fun ModeChip(text: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(BrandGradient)
                } else {
                    Modifier.background(YomiraColors.Surface.copy(alpha = 0.55f))
                },
            )
            .border(
                1.dp,
                if (active) Color.Transparent else YomiraColors.OutlineSoft.copy(alpha = 0.8f),
                shape,
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = if (active) Color.White else YomiraColors.TextMuted,
        )
    }
}

@Composable
private fun ReaderProgressCapsule(
    page: Int,
    total: Int,
    onJumpTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = (page.toFloat() / total).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .widthIn(max = 640.dp)
            .height(54.dp)
            .clip(shape)
            .background(YomiraColors.OledRaised.copy(alpha = 0.88f))
            .border(1.dp, YomiraColors.OutlineSoft.copy(alpha = 0.9f), shape)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NumericHud("SAYFA ${page.toString().padStart(3, '0')}", color = YomiraColors.TextSecondary)
        Text("/", style = NumericStyle.copy(fontSize = 11.sp), color = YomiraColors.OutlineSoft)
        Text(
            total.toString().padStart(3, '0'),
            style = NumericStyle.copy(fontSize = 11.sp),
            color = YomiraColors.TextMuted,
        )
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(YomiraColors.SurfaceHighest),
        ) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight().background(BrandGradient),
            )
        }
        MetaChip(
            text = "${(progress * 100).toInt()}%",
            accent = YomiraColors.Lavender,
        )
        IconBubble(
            icon = Icons.Filled.ArrowUpward,
            contentDescription = DesktopCopy.JUMP_TOP,
            onClick = onJumpTop,
            size = 38.dp,
            background = Color.Transparent,
        )
    }
}

@Composable
private fun ReaderHint(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 1_280.dp) {
            Text(
                DesktopCopy.READER_HINT,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = YomiraColors.TextMuted.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ReaderStatus(
    title: String,
    message: String,
    loading: Boolean,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            Spinner(size = 28.dp)
        } else {
            Icon(Icons.Outlined.ErrorOutline, null, tint = YomiraColors.Gold, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = YomiraColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = YomiraColors.TextMuted)
        if (!loading && onRetry != null) {
            Spacer(Modifier.height(18.dp))
            NeonButton(DesktopCopy.RETRY, onRetry, icon = Icons.Filled.Refresh)
        }
    }
}

@Composable
private fun WebtoonPage(page: DesktopPage, client: OkHttpClient) {
    var aspect by remember(page.url) { mutableFloatStateOf(0.66f) }
    Box(
        Modifier
            // widthIn must come first: a leading fillMaxWidth would pin the
            // minimum to the whole canvas and the cap would never apply.
            .widthIn(max = 1_020.dp)
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(YomiraColors.ReaderCanvas),
    ) {
        RemoteImage(
            url = page.url,
            client = client,
            contentDescription = "Sayfa ${page.index + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillWidth,
            headers = page.headers,
            onLoaded = { bitmap ->
                if (bitmap.width > 0 && bitmap.height > 0) {
                    aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                }
            },
        )
    }
}

@Composable
private fun PagedPage(page: DesktopPage, client: OkHttpClient, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxHeight().padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(YomiraColors.ReaderCanvas),
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            url = page.url,
            client = client,
            contentDescription = "Sayfa ${page.index + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            headers = page.headers,
        )
    }
}
