package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
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
 * (left-to-right) order, and a two-page spread for manga. All key handling goes
 * through [readerIntent] / [stepForArrow], the pure table covered by
 * `DesktopReaderNavigationTest`.
 */
@Composable
fun DesktopReaderScreen(
    state: DesktopAppState,
    client: OkHttpClient,
    modifier: Modifier = Modifier,
) {
    val session = state.reader
    if (session == null) {
        state.closeReader()
        return
    }

    var layout by remember(session.chapter.id) { mutableStateOf(DesktopReadingLayout.WEBTOON) }
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
            .background(Color.Black)
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
                contentPadding = PaddingValues(top = 96.dp, bottom = 92.dp),
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
                    contentPadding = PaddingValues(top = 84.dp, bottom = 80.dp),
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

        // Top scrim keeps the HUD legible over bright, tall webtoon art.
        Box(
            Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter).background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.94f),
                    0.62f to Color.Black.copy(alpha = 0.42f),
                    1f to Color.Transparent,
                ),
            ),
        )
        ReaderHud(
            state = state,
            layout = layout,
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
            onJumpTop = {
                when (layout) {
                    DesktopReadingLayout.WEBTOON -> scope.launch { listState.animateScrollToItem(0) }
                    else -> scope.launch { pagerState.animateScrollToPage(0) }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (session.pages.isNotEmpty()) {
            ReaderProgress(
                page = (state.readerPage + 1).coerceAtMost(session.pages.size),
                total = session.pages.size,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ReaderHud(
    state: DesktopAppState,
    layout: DesktopReadingLayout,
    onLayoutChange: (DesktopReadingLayout) -> Unit,
    onClose: () -> Unit,
    onJumpTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.reader ?: return
    Row(
        modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBubble(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = DesktopCopy.BACK,
            onClick = onClose,
            size = 40.dp,
            background = Color.Black.copy(alpha = 0.55f),
        )
        Column(Modifier.widthIn(max = 420.dp)) {
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
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        if (session.pages.isNotEmpty()) {
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, YomiraColors.Outline.copy(alpha = 0.8f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NumericHud("${state.readerPage + 1}".padStart(3, '0'))
                Text("/", style = NumericStyle.copy(fontSize = 11.sp), color = YomiraColors.Outline)
                NumericHud(session.pages.size.toString().padStart(3, '0'), color = YomiraColors.TextMuted)
            }
        }
        IconBubble(
            icon = Icons.Filled.ArrowUpward,
            contentDescription = DesktopCopy.JUMP_TOP,
            onClick = onJumpTop,
            size = 40.dp,
            background = Color.Black.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ModeChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (active) YomiraColors.Purple else Color.Black.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (active) YomiraColors.Purple else YomiraColors.Outline.copy(alpha = 0.8f),
                CircleShape,
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else YomiraColors.TextMuted,
        )
    }
}

@Composable
private fun ReaderProgress(page: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp))
                .background(YomiraColors.Outline.copy(alpha = 0.5f)),
        ) {
            Box(
                Modifier.fillMaxWidth((page.toFloat() / total).coerceIn(0f, 1f)).height(2.dp)
                    .background(Brush.horizontalGradient(listOf(YomiraColors.Purple, YomiraColors.Fuchsia))),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(DesktopCopy.READER_HINT, style = MaterialTheme.typography.labelSmall, color = YomiraColors.TextMuted)
            Spacer(Modifier.weight(1f))
            NumericHud("SAYFA $page / $total", color = YomiraColors.TextMuted)
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
            .fillMaxWidth()
            .widthIn(max = 900.dp)
            .aspectRatio(aspect)
            .background(Color.Black),
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
        modifier.fillMaxHeight().padding(horizontal = 2.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black),
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
