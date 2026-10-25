package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.model.ReaderSession
import com.yomira.desktop.state.DesktopAppState
import kotlin.math.abs
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** How the reader lays out a chapter. Arrow-key meaning follows the layout. */
enum class DesktopReadingLayout {
    /** Manga: `←` turns forward. */
    PAGED_RIGHT_TO_LEFT,

    /** Western comics: `→` turns forward. */
    PAGED_LEFT_TO_RIGHT,

    /** Two pages per spread, stepping a spread at a time. */
    DOUBLE_PAGE,

    /** Vertical webtoon strip; wheel and arrows scroll instead of paging. */
    WEBTOON,
}

/** What one physical key means for the active reader layout. */
internal enum class ReaderIntent { NONE, PRE, NEXT, FIRST, LAST, SCROLL_UP, SCROLL_DOWN }

/**
 * Pure key table of the reader. Kept separate from the UI so the same contract
 * shown in the README is unit-testable (see `DesktopNavigationTest`).
 */
internal fun readerIntent(key: Key, layout: DesktopReadingLayout): ReaderIntent = when (layout) {
    DesktopReadingLayout.WEBTOON -> when (key) {
        Key.DirectionUp, Key.PageUp -> ReaderIntent.SCROLL_UP
        Key.DirectionDown, Key.PageDown, Key.Spacebar -> ReaderIntent.SCROLL_DOWN
        Key.MoveHome -> ReaderIntent.FIRST
        Key.MoveEnd -> ReaderIntent.LAST
        else -> ReaderIntent.NONE
    }

    else -> when (key) {
        Key.DirectionLeft -> ReaderIntent.PRE
        Key.DirectionRight -> ReaderIntent.NEXT
        Key.DirectionUp, Key.PageUp -> ReaderIntent.PRE
        Key.DirectionDown, Key.PageDown, Key.Spacebar -> ReaderIntent.NEXT
        Key.MoveHome -> ReaderIntent.FIRST
        Key.MoveEnd -> ReaderIntent.LAST
        else -> ReaderIntent.NONE
    }
}

/** `←` advances (manga order) in RTL layouts and rewinds in LTR ones. */
internal fun stepForArrow(intent: ReaderIntent, layout: DesktopReadingLayout): Int {
    val stride = if (layout == DesktopReadingLayout.DOUBLE_PAGE) 2 else 1
    return when (intent) {
        ReaderIntent.PRE -> if (layout == DesktopReadingLayout.PAGED_RIGHT_TO_LEFT) stride else -stride
        ReaderIntent.NEXT -> if (layout == DesktopReadingLayout.PAGED_RIGHT_TO_LEFT) -stride else stride
        else -> 0
    }
}

/**
 * Full-bleed chapter reader.
 *
 * - `←` / `→` / `Space` / `PgUp` / `PgDn` / `Home` / `End` paginate (or scroll a
 *   webtoon) exactly per [readerIntent]; `Esc` returns to the detail pane.
 * - The mouse wheel glides the webtoon strip and flips pages in paged layouts.
 * - `DOUBLE_PAGE` stitches two pages side by side and steps spreads.
 */
@Composable
fun DesktopReaderScreen(state: DesktopAppState, client: OkHttpClient, modifier: Modifier = Modifier) {
    val session = state.reader ?: return
    var layout by rememberSaveable(session.item.id) {
        mutableStateOf(DesktopReadingLayout.PAGED_RIGHT_TO_LEFT)
    }
    var page by remember(session.chapter.id) { mutableIntStateOf(state.readerPage) }
    val pages = session.pages
    val lastIndex = (pages.size - 1).coerceAtLeast(0)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var viewportHeight by remember { mutableFloatStateOf(900f) }
    var wheelAccumulator by remember { mutableFloatStateOf(0f) }

    fun goTo(index: Int) {
        val target = index.coerceIn(0, lastIndex)
        page = target
        state.updateReaderPage(target)
        if (layout == DesktopReadingLayout.WEBTOON) scope.launch { listState.scrollToItem(target) }
    }

    fun step(delta: Int) = goTo(page + delta)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportHeight = it.height.toFloat() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (val intent = readerIntent(event.key, layout)) {
                    ReaderIntent.NONE -> false
                    ReaderIntent.PRE, ReaderIntent.NEXT -> {
                        step(stepForArrow(intent, layout))
                        true
                    }
                    ReaderIntent.FIRST -> { goTo(0); true }
                    ReaderIntent.LAST -> { goTo(lastIndex); true }
                    ReaderIntent.SCROLL_UP -> {
                        scope.launch { listState.scrollBy(-viewportHeight * 0.9f) }
                        true
                    }
                    ReaderIntent.SCROLL_DOWN -> {
                        scope.launch { listState.scrollBy(viewportHeight * 0.9f) }
                        true
                    }
                }
            }
            .pointerInput(session.chapter.id, layout, pages.size) {
                if (layout == DesktopReadingLayout.WEBTOON) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                            if (delta.y != 0f) {
                                wheelAccumulator += delta.y
                                if (abs(wheelAccumulator) >= 1.2f) {
                                    step(if (wheelAccumulator > 0f) 1 else -1)
                                    wheelAccumulator = 0f
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            },
    ) {
        when {
            session.loading -> LoadingBlock(DesktopCopy.PAGES_LOADING, Modifier.align(Alignment.Center))
            session.error != null -> MessageBlock(
                text = session.error,
                modifier = Modifier.align(Alignment.Center),
                retryText = DesktopCopy.RETRY,
                onRetry = state::retryPages,
            )
            pages.isEmpty() -> MessageBlock(DesktopCopy.CHAPTERS_EMPTY, Modifier.align(Alignment.Center))
            layout == DesktopReadingLayout.WEBTOON -> WebtoonStrip(
                session = session,
                client = client,
                listState = listState,
                onPageChanged = { index -> page = index; state.updateReaderPage(index) },
            )
            layout == DesktopReadingLayout.DOUBLE_PAGE -> SpreadPages(
                session = session,
                client = client,
                spreadStart = (page / 2) * 2,
            )
            else -> Box(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 32.dp)) {
                val current = pages.getOrNull(page)
                RemoteImage(
                    url = current?.url,
                    client = client,
                    key = current?.url,
                    headers = current?.headers.orEmpty(),
                    contentDescription = "${DesktopCopy.PAGE} ${page + 1}",
                    modifier = Modifier.matchParentSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }

        ReaderTopBar(
            session = session,
            page = page,
            layout = layout,
            onClose = state::closeReader,
            onLayoutChange = { next ->
                layout = next
                goTo(page)
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        HintFooter(DesktopCopy.READER_HINT, Modifier.align(Alignment.BottomCenter))
    }
}

/** Vertical infinite strip; the platform wheel scroll drives the LazyColumn. */
@Composable
private fun WebtoonStrip(
    session: ReaderSession,
    client: OkHttpClient,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPageChanged: (Int) -> Unit,
) {
    LaunchedEffect(listState, session.chapter.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect(onPageChanged)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        item { Spacer(Modifier.height(56.dp)) }
        items(session.pages, key = { it.index }) { pageItem ->
            RemoteImage(
                url = pageItem.url,
                client = client,
                key = pageItem.url,
                headers = pageItem.headers,
                contentDescription = "${DesktopCopy.PAGE} ${pageItem.index + 1}",
                modifier = Modifier.fillMaxWidth(),
                fillParent = false,
            )
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

/** Two pages side by side for the spread mode. */
@Composable
private fun SpreadPages(session: ReaderSession, client: OkHttpClient, spreadStart: Int) {
    Row(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 32.dp)) {
        val left = session.pages.getOrNull(spreadStart)
        val right = session.pages.getOrNull(spreadStart + 1)
        listOf(left, right).forEach { pageItem ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
                RemoteImage(
                    url = pageItem?.url,
                    client = client,
                    key = pageItem?.url,
                    headers = pageItem?.headers.orEmpty(),
                    contentDescription = pageItem?.let { "${DesktopCopy.PAGE} ${it.index + 1}" },
                    modifier = Modifier.matchParentSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
    }
}

/** Translucent command strip: exit, context, page counter, layout switcher. */
@Composable
private fun ReaderTopBar(
    session: ReaderSession,
    page: Int,
    layout: DesktopReadingLayout,
    onClose: () -> Unit,
    onLayoutChange: (DesktopReadingLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC050409))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = DesktopCopy.CLOSE, tint = YomiraColors.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(session.item.title, color = YomiraColors.Ink, fontSize = 13.sp, maxLines = 1)
            Text(session.chapter.displayLabel, color = YomiraColors.InkMuted, fontSize = 11.sp, maxLines = 1)
        }
        if (session.pages.isNotEmpty()) {
            Text(
                "${page + 1} / ${session.pages.size}",
                color = YomiraColors.Cyan,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        LayoutChoice(DesktopCopy.MODE_MANGA, layout == DesktopReadingLayout.PAGED_RIGHT_TO_LEFT) {
            onLayoutChange(DesktopReadingLayout.PAGED_RIGHT_TO_LEFT)
        }
        LayoutChoice(DesktopCopy.MODE_WESTERN, layout == DesktopReadingLayout.PAGED_LEFT_TO_RIGHT) {
            onLayoutChange(DesktopReadingLayout.PAGED_LEFT_TO_RIGHT)
        }
        LayoutChoice(DesktopCopy.MODE_SPREAD, layout == DesktopReadingLayout.DOUBLE_PAGE) {
            onLayoutChange(DesktopReadingLayout.DOUBLE_PAGE)
        }
        LayoutChoice(DesktopCopy.MODE_WEBTOON, layout == DesktopReadingLayout.WEBTOON) {
            onLayoutChange(DesktopReadingLayout.WEBTOON)
        }
    }
}

@Composable
private fun LayoutChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            color = if (selected) YomiraColors.Cyan else YomiraColors.InkMuted,
            fontSize = 11.sp,
        )
    }
}
