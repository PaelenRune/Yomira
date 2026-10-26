package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.core.model.ContentRating
import com.yomira.core.network.MetadataSources
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.model.desktopLabel
import com.yomira.desktop.model.toDisplayNumber
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.heroItem
import java.util.Locale
import okhttp3.OkHttpClient

/**
 * Series dossier with master-detail geometry: the chapter spine is a fixed left
 * pane, the cover, credits and synopsis fill the remaining width. On narrow
 * windows the chapter pane collapses to a readable minimum and the dossier
 * scrolls independently, so a 1024px window never crushes either side.
 */
@Composable
fun DesktopDetailScreen(
    state: DesktopAppState,
    client: OkHttpClient,
    onOpenChapter: (DesktopChapter) -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.heroItem()
    if (item == null) {
        // The selected card vanished (for example after a failed reload): fall
        // back to the grid instead of painting an empty dossier.
        state.closeDetail()
        return
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = ShellPadding, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBubble(Icons.AutoMirrored.Filled.ArrowBack, DesktopCopy.BACK, state::closeDetail, size = 36.dp)
            SectionLabel("SERÄ° DOSYASI")
            Text(
                item.title,
                color = YomiraColors.TextMuted,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MetaChip(text = sourceLabel(item.sourceId))
        }
        Divider()

        Row(Modifier.weight(1f)) {
            ChapterPane(
                state = state,
                item = item,
                onOpenChapter = onOpenChapter,
                onOpenBrowser = onOpenBrowser,
                modifier = Modifier.fillMaxHeight().widthIn(min = 300.dp, max = 380.dp),
            )
            Box(Modifier.fillMaxHeight().padding(vertical = 18.dp).width(1.dp).background(Hairline))
            DossierPane(
                item = item,
                client = client,
                loading = state.detailLoading,
                message = state.detailError ?: state.chaptersMessage,
                onRead = { state.chapterList.firstOrNull(DesktopChapter::isReadable)?.let(onOpenChapter) },
                onOpenBrowser = onOpenBrowser,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ChapterPane(
    state: DesktopAppState,
    item: CatalogItem,
    onOpenChapter: (DesktopChapter) -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = YomiraColors.Purple, modifier = Modifier.size(17.dp))
            Text(
                DesktopCopy.CHAPTERS,
                color = YomiraColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            if (state.chaptersLoading) {
                Spinner(size = 14.dp)
            } else if (state.chapterList.isNotEmpty()) {
                NumericHud("${state.chapterList.size}")
            }
        }

        val message = state.chaptersMessage
        when {
            state.chaptersLoading && state.chapterList.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Spinner() }

            message != null && state.chapterList.isEmpty() -> MessageBlock(
                message = message,
                showSource = !item.isReadableOnDesktop && item.sourceUrl != null,
                onOpenSource = { item.sourceUrl?.let(onOpenBrowser) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.chapterList, key = { it.id }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        onOpen = {
                            if (chapter.isReadable) onOpenChapter(chapter)
                            else chapter.externalUrl?.let(onOpenBrowser)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: DesktopChapter, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) YomiraColors.SurfaceRaised else Color.Transparent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            chapter.number.toDisplayNumber(),
            style = NumericStyle.copy(fontSize = 11.sp),
            color = YomiraColors.TextMuted,
            modifier = Modifier.widthIn(min = 24.dp),
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title?.takeIf { it.isNotBlank() } ?: "BÃ¶lÃ¼m ${chapter.number.toDisplayNumber()}",
                color = YomiraColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildList {
                    chapter.group?.let { add(it) }
                    if (chapter.pageCount > 0) add(DesktopCopy.CHAPTER_PAGES.format(chapter.pageCount))
                    if (!chapter.isReadable) add(DesktopCopy.EXTERNAL_CHAPTER)
                }.joinToString("  Â·  "),
                color = YomiraColors.TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (chapter.isReadable) Icons.Filled.PlayArrow else Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = if (chapter.isReadable) DesktopCopy.READ else DesktopCopy.OPEN_EXTERNAL,
            tint = if (chapter.isReadable) YomiraColors.Purple else YomiraColors.TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DossierPane(
    item: CatalogItem,
    client: OkHttpClient,
    loading: Boolean,
    message: String?,
    onRead: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(YomiraColors.Surface)
                .border(1.dp, YomiraColors.Outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
        ) {
            RemoteImage(
                url = item.bannerUrl ?: item.coverUrl,
                client = client,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to YomiraColors.Oled.copy(alpha = 0.94f),
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.title,
                    color = YomiraColors.TextPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.author?.let {
                    Text(it, color = YomiraColors.TextMuted, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                Modifier
                    .size(width = 168.dp, height = 246.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(YomiraColors.Surface)
                    .border(1.dp, YomiraColors.Outline, RoundedCornerShape(14.dp)),
            ) {
                RemoteImage(
                    url = item.coverUrl,
                    client = client,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    item.score?.let { score ->
                        MetaChip(
                            text = String.format(Locale.ROOT, "%.1f / 10", score),
                            accent = YomiraColors.Gold,
                            leading = Icons.Filled.Star,
                        )
                    }
                    item.year?.let { MetaChip(it.toString()) }
                    item.status.desktopLabel()?.let { MetaChip(it) }
                    if (item.contentRating != ContentRating.SAFE) {
                        MetaChip("MATURE", accent = YomiraColors.Fuchsia)
                    }
                }
                if (item.genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.genres.forEach { MetaChip(it) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeonButton(
                        text = DesktopCopy.READ,
                        icon = Icons.Filled.PlayArrow,
                        onClick = onRead,
                        enabled = item.isReadableOnDesktop,
                    )
                    GhostButton(
                        text = DesktopCopy.OPEN_SOURCE,
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = {
                            val url = item.sourceUrl ?: defaultSourceUrl(item)
                            if (url != null) onOpenBrowser(url)
                        },
                    )
                }
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spinner(size = 13.dp)
                        Text("Åelale birleÅŸtiriliyorâ€¦", color = YomiraColors.TextMuted, fontSize = 11.sp)
                    }
                }
                message?.takeIf { !item.isReadableOnDesktop }?.let {
                    Text(it, color = YomiraColors.Gold, fontSize = 11.5.sp, lineHeight = 17.sp)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(DesktopCopy.SYNOPSIS)
            Text(
                item.synopsis ?: "Bu seri iÃ§in aÃ§Ä±klama paylaÅŸÄ±lmamÄ±ÅŸ.",
                color = YomiraColors.TextMuted,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                modifier = Modifier.widthIn(max = 820.dp),
            )
        }

        Text(
            "Kapaklar ve sayfalar uzak HTTPS kaynaklarÄ±ndan akÄ±ÅŸla yÃ¼klenir; uygulamayla daÄŸÄ±tÄ±lmaz.",
            color = YomiraColors.TextMuted.copy(alpha = 0.62f),
            fontSize = 10.5.sp,
        )
    }
}

@Composable
private fun MessageBlock(
    message: String,
    showSource: Boolean,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(YomiraColors.Surface.copy(alpha = 0.72f))
            .border(1.dp, YomiraColors.Outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = YomiraColors.Gold, modifier = Modifier.size(18.dp))
            Text(message, color = YomiraColors.TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (showSource) {
            GhostButton(DesktopCopy.OPEN_EXTERNAL, onOpenSource, icon = Icons.AutoMirrored.Filled.OpenInNew)
        }
    }
}

/** Canonical provider page so "open source" works even for cards without a URL. */
private fun defaultSourceUrl(item: CatalogItem): String? = when (item.sourceId) {
    MetadataSources.MANGADEX -> "https://mangadex.org/title/${item.remoteId}"
    MetadataSources.ANILIST -> "https://anilist.co/manga/${item.remoteId}"
    MetadataSources.BAKA_UPDATES -> "https://www.mangaupdates.com/series.html?id=${item.remoteId}"
    MetadataSources.KITSU -> "https://kitsu.io/manga/${item.remoteId}"
    MetadataSources.COMICVINE -> "https://comicvine.gamespot.com/volume/4050-${item.remoteId}/"
    MetadataSources.OPENLIBRARY -> "https://openlibrary.org/works/${item.remoteId}"
    else -> item.sourceUrl
}

private fun sourceLabel(sourceId: String): String = when (sourceId) {
    MetadataSources.MANGADEX -> "MangaDex"
    MetadataSources.ANILIST -> "AniList"
    MetadataSources.BAKA_UPDATES -> "MangaUpdates"
    MetadataSources.KITSU -> "Kitsu"
    MetadataSources.COMICVINE -> "ComicVine"
    MetadataSources.OPENLIBRARY -> "OpenLibrary"
    else -> sourceId
}

