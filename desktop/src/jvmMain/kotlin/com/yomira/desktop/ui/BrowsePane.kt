package com.yomira.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.model.desktopLabel
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.FeedSource
import java.util.Locale
import okhttp3.OkHttpClient

/**
 * The poster grid. Column count comes from
 * [com.yomira.desktop.state.PosterGrid] so 2K/4K displays get the intended 6-8
 * columns, while a resized window keeps legible covers. Everything scrolls
 * inside one lazy grid: the spotlight is a full-span item, not a second scroller.
 */
@Composable
fun BrowsePane(
    state: DesktopAppState,
    columns: Int,
    client: OkHttpClient,
    onSelect: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.gridResetToken) {
        if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
            gridState.scrollToItem(0)
        }
    }

    val search = state.feedSource as? FeedSource.Search
    val hero = (state.feedSource as? FeedSource.Browse)
        ?.takeIf { state.cursor.page == 1 }
        ?.let { state.items.firstOrNull() }
    val cards = if (hero != null) state.items.drop(1) else state.items

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = ShellPadding, end = ShellPadding, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(if (search != null) "ARAMA SONUÇLARI" else "TREND KATALOĞU")
            Box(Modifier.height(1.dp).weight(1f).background(Hairline))
            when {
                state.feedLoading -> Spinner(size = 15.dp)
                search != null -> NumericHud("${state.items.size} SONUÇ")
                else -> NumericHud("${state.items.size} SERİ")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.feedLoading && state.items.isEmpty() -> SkeletonGrid(columns)
                state.feedError != null -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "Bağlantı kurulamadı",
                    body = state.feedError ?: DesktopCopy.FEED_FAILED,
                    actionLabel = DesktopCopy.RETRY,
                    onAction = state::retry,
                )
                state.items.isEmpty() -> EmptyState(
                    icon = if (search != null) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                    title = if (search != null) "Sonuç yok" else "Katalog boş",
                    body = if (search != null) DesktopCopy.SEARCH_EMPTY else DesktopCopy.FEED_EMPTY,
                    actionLabel = if (search != null) "Keşfet'e dön" else DesktopCopy.RETRY,
                    onAction = if (search != null) state::clearSearch else state::retry,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ShellPadding, end = ShellPadding, top = 10.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    if (hero != null) {
                        item(key = "hero-${hero.id}", span = { GridItemSpan(maxLineSpan) }) {
                            HeroCard(hero, client, onDetails = { onSelect(hero) })
                        }
                    }
                    items(cards, key = { it.id }) { item ->
                        PosterCard(item, client, onClick = { onSelect(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    item: CatalogItem,
    client: OkHttpClient,
    onDetails: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(392.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, YomiraColors.Outline.copy(alpha = 0.8f), RoundedCornerShape(26.dp)),
    ) {
        val wide = maxWidth >= 1_000.dp
        RemoteImage(
            url = item.bannerUrl ?: item.coverUrl,
            client = client,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to YomiraColors.Oled.copy(alpha = 0.96f),
                    0.5f to YomiraColors.Oled.copy(alpha = 0.72f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to YomiraColors.Oled.copy(alpha = 0.92f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(horizontal = if (wide) 40.dp else 28.dp, vertical = 30.dp)
                .widthIn(max = 660.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("ÖNE ÇIKAN")
                item.score?.let { score ->
                    MetaChip(
                        text = String.format(Locale.ROOT, "%.1f", score),
                        accent = YomiraColors.Gold,
                        leading = Icons.Filled.Star,
                    )
                }
                item.year?.let { MetaChip(text = it.toString()) }
                item.status.desktopLabel()?.let { MetaChip(text = it) }
            }
            Text(
                text = item.title,
                style = if (wide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                color = YomiraColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.synopsis?.let { synopsis ->
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = YomiraColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                NeonButton(
                    text = "Detaylar",
                    icon = Icons.Filled.PlayArrow,
                    onClick = onDetails,
                )
                if (item.genres.isNotEmpty()) {
                    Text(
                        text = item.genres.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = YomiraColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCard(
    item: CatalogItem,
    client: OkHttpClient,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.028f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "poster-scale",
    )
    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(YomiraColors.Surface)
                .border(
                    width = 1.dp,
                    color = if (hovered) YomiraColors.Purple.copy(alpha = 0.65f) else YomiraColors.Outline.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(18.dp),
                ),
        ) {
            RemoteImage(
                url = item.coverUrl,
                client = client,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to YomiraColors.Oled.copy(alpha = 0.85f),
                    ),
                ),
            )
            item.score?.let { score ->
                Row(
                    Modifier.align(Alignment.TopEnd).padding(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(YomiraColors.Oled.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Filled.Star, null, tint = YomiraColors.Gold, modifier = Modifier.size(10.dp))
                    Text(
                        String.format(Locale.ROOT, "%.1f", score),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = YomiraColors.TextPrimary,
                    )
                }
            }
            if (hovered) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(10.dp)
                        .clip(CircleShape)
                        .background(YomiraColors.Purple.copy(alpha = 0.92f))
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "DETAY",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = YomiraColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            item.year?.let {
                Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = YomiraColors.TextMuted)
            }
            item.status.desktopLabel()?.let {
                Box(Modifier.size(3.dp).clip(CircleShape).background(YomiraColors.Outline))
                Text(it, style = MaterialTheme.typography.labelSmall, color = YomiraColors.TextMuted)
            }
            item.author?.let {
                Box(Modifier.size(3.dp).clip(CircleShape).background(YomiraColors.Outline))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = YomiraColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SkeletonGrid(columns: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ShellPadding, end = ShellPadding, top = 10.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        userScrollEnabled = false,
    ) {
        items(count = columns * 2) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(YomiraColors.Surface, YomiraColors.SurfaceRaised, YomiraColors.Surface),
                            ),
                        ),
                )
                Box(Modifier.fillMaxWidth(0.78f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(YomiraColors.Surface))
                Box(Modifier.fillMaxWidth(0.4f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(YomiraColors.Surface))
            }
        }
    }
}

@Composable
fun PagerFooter(
    state: DesktopAppState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ShellPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                DesktopCopy.GRID_HINT,
                style = MaterialTheme.typography.labelSmall,
                color = YomiraColors.TextMuted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.feedSource is FeedSource.Browse) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBubble(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = DesktopCopy.PREVIOUS,
                        onClick = state::previousPage,
                        enabled = state.canGoPrevious,
                        size = 34.dp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        SectionLabel(DesktopCopy.PAGE)
                        NumericHud(text = state.cursor.page.toString().padStart(2, '0'))
                    }
                    IconBubble(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = DesktopCopy.NEXT,
                        onClick = state::nextPage,
                        enabled = state.canGoNext,
                        size = 34.dp,
                    )
                }
            } else {
                NumericHud("WEB ŞELALESİ · ${state.items.size} SONUÇ")
            }
        }
    }
}
