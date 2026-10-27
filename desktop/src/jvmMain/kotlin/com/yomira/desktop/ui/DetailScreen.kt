package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
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
 * pane, the cover, credits and synopsis fill the remaining width. Below 640dp the
 * screen switches to a single scrolling column: the dossier comes first (cover,
 * title, read action, synopsis) and the chapter spine follows, so nothing is
 * pushed off a phone-width canvas.
 */
@Composable
fun DesktopDetailScreen(
    state: DesktopAppState,
    client: OkHttpClient,
    onOpenChapter: (DesktopChapter) -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Other wall cards, shown as a "Benzer seriler" rail under the synopsis. */
    related: List<CatalogItem> = emptyList(),
) {
    val item = state.heroItem()
    if (item == null) {
        // The selected card vanished (for example after a failed reload): fall
        // back to the grid instead of painting an empty dossier.
        state.closeDetail()
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < CompactBreakpoint
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 16.dp else 20.dp,
                        end = if (compact) 16.dp else ShellPadding,
                        top = 12.dp,
                        bottom = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBubble(Icons.AutoMirrored.Filled.ArrowBack, DesktopCopy.BACK, state::closeDetail, size = 36.dp)
                if (!compact) SectionLabel("SERİ DOSYASI")
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

            if (compact) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                ) {
                    item(key = "dossier") {
                        DossierPane(
                            item = item,
                            client = client,
                            loading = state.detailLoading,
                            message = state.detailError ?: state.chaptersMessage,
                            onRead = { state.chapterList.firstOrNull(DesktopChapter::isReadable)?.let(onOpenChapter) },
                            onOpenBrowser = onOpenBrowser,
                            compact = true,
                            scrollable = false,
                            related = related,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "chapters-header") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                            ChapterHeader(state, Modifier.padding(top = 14.dp, bottom = 2.dp))
                        }
                    }
                    chapterRows(
                        state,
                        item,
                        onOpenChapter,
                        onOpenBrowser,
                        rowModifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            } else {
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
                        related = related,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** Below this width the two-pane dossier cannot fit; stack it instead. */
private val CompactBreakpoint = 640.dp

/** The dossier stops growing here so a 4K canvas keeps the text column readable. */
private val DossierMaxWidth = 1_180.dp

@Composable
private fun ChapterHeader(state: DesktopAppState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
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
}

/** Chapter list slots, shared by the fixed spine and the phone-width column. */
private fun LazyListScope.chapterRows(
    state: DesktopAppState,
    item: CatalogItem,
    onOpenChapter: (DesktopChapter) -> Unit,
    onOpenBrowser: (String) -> Unit,
    rowModifier: Modifier = Modifier,
) {
    val message = state.chaptersMessage
    when {
        state.chaptersLoading && state.chapterList.isEmpty() -> item(key = "chapters-loading") {
            Box(
                Modifier.fillMaxWidth().padding(top = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Spinner() }
        }

        message != null && state.chapterList.isEmpty() -> item(key = "chapters-message") {
            MessageBlock(
                message = message,
                showSource = !item.isReadableOnDesktop && item.sourceUrl != null,
                onOpenSource = { item.sourceUrl?.let(onOpenBrowser) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        else -> items(state.chapterList, key = { it.id }) { chapter ->
            ChapterRow(
                chapter = chapter,
                onOpen = {
                    if (chapter.isReadable) onOpenChapter(chapter)
                    else chapter.externalUrl?.let(onOpenBrowser)
                },
                modifier = rowModifier,
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
        ChapterHeader(state)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            chapterRows(state, item, onOpenChapter, onOpenBrowser)
        }
    }
}

@Composable
private fun ChapterRow(chapter: DesktopChapter, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (hovered) YomiraColors.SurfaceRaised else Color.Transparent)
            .then(
                if (hovered) {
                    Modifier.border(1.dp, YomiraColors.Purple.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            )
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
                chapter.title?.takeIf { it.isNotBlank() } ?: "Bölüm ${chapter.number.toDisplayNumber()}",
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
                }.joinToString("  ·  "),
                color = YomiraColors.TextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (chapter.isReadable) Icons.Filled.PlayArrow else Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = if (chapter.isReadable) DesktopCopy.READ else DesktopCopy.OPEN_EXTERNAL,
            tint = if (chapter.isReadable) YomiraColors.Lavender else YomiraColors.TextMuted,
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
    compact: Boolean = false,
    scrollable: Boolean = true,
    related: List<CatalogItem> = emptyList(),
) {
    val scrollState = rememberScrollState()
    Column(
        modifier
            .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(horizontal = if (compact) 16.dp else 30.dp, vertical = if (compact) 16.dp else 22.dp),
    ) {
        Column(
            Modifier.widthIn(max = DossierMaxWidth),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 200.dp else 240.dp)
                    .clip(YomiraShape.Panel)
                    .background(YomiraColors.Surface)
                    .border(1.dp, YomiraColors.Outline.copy(alpha = 0.6f), YomiraShape.Panel),
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
                            0.3f to Color.Transparent,
                            1f to YomiraColors.Oled.copy(alpha = 0.94f),
                        ),
                    ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(if (compact) 16.dp else 24.dp)
                        .widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        item.title,
                        color = YomiraColors.TextPrimary,
                        style = if (compact) {
                            MaterialTheme.typography.headlineMedium
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.author?.let {
                        Text(it, color = YomiraColors.TextMuted, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (compact) {
                // Cover and facts side by side, actions on their own row so the
                // primary "read" pill keeps its full width on a narrow canvas.
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        DossierCover(item, client, Modifier.size(width = 112.dp, height = 164.dp))
                        DossierFacts(item, Modifier.weight(1f))
                    }
                    DossierActions(item, onRead, onOpenBrowser)
                    DossierStatus(item, loading, message)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    DossierCover(item, client, Modifier.size(width = 168.dp, height = 246.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DossierFacts(item)
                        DossierActions(item, onRead, onOpenBrowser)
                        DossierStatus(item, loading, message)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline.copy(alpha = 0.7f)))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(DesktopCopy.SYNOPSIS)
                Text(
                    item.synopsis ?: "Bu seri için açıklama paylaşılmamış.",
                    color = YomiraColors.TextSecondary.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.widthIn(max = 820.dp),
                )
            }

            if (!compact) {
                DossierStats(item, client)
            }

            if (related.isNotEmpty()) {
                RelatedRail(related, client)
            }

            Text(
                "Kapaklar ve sayfalar uzak HTTPS kaynaklarından akışla yüklenir; uygulamayla dağıtılmaz.",
                color = YomiraColors.TextMuted.copy(alpha = 0.62f),
                fontSize = 10.5.sp,
            )
        }
    }
}

/**
 * Three quiet stat tiles under the synopsis. They are derived from the card
 * alone (year, rating, genres), so the dossier never shows a placeholder number
 * the catalogue did not publish.
 */
@Composable
private fun DossierStats(item: CatalogItem, client: OkHttpClient) {
    Row(
        Modifier.fillMaxWidth().widthIn(max = 820.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            label = "PUAN",
            value = item.score?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—",
            hint = "10 üzerinden",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "YIL",
            value = item.year?.toString() ?: "—",
            hint = item.status.desktopLabel() ?: "Bilinmiyor",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "TÜRLER",
            value = item.genres.size.toString().padStart(2, '0'),
            hint = item.genres.take(2).joinToString(" · ").ifEmpty { "Etiket yok" },
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "KAYNAK",
            value = sourceLabel(item.sourceId),
            hint = "Uzak katalog",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(YomiraShape.Control)
            .background(YomiraColors.Surface.copy(alpha = 0.66f))
            .border(1.dp, YomiraColors.Outline.copy(alpha = 0.8f), YomiraShape.Control)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = YomiraColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = YomiraColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Horizontal "Benzer seriler" strip: the rest of the wall, one click away. */
@Composable
private fun RelatedRail(items: List<CatalogItem>, client: OkHttpClient) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("BENZER SERİLER")
            Box(Modifier.height(1.dp).weight(1f).background(Hairline.copy(alpha = 0.6f)))
            NumericHud("${items.size} SERİ")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { entry ->
                Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(YomiraColors.Surface)
                            .border(1.dp, YomiraColors.Outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    ) {
                        RemoteImage(
                            url = entry.coverUrl,
                            client = client,
                            contentDescription = entry.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = YomiraColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DossierCover(item: CatalogItem, client: OkHttpClient, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(YomiraShape.Control)
            .background(YomiraColors.Surface)
            .border(1.dp, YomiraColors.OutlineSoft, YomiraShape.Control),
    ) {
        RemoteImage(
            url = item.coverUrl,
            client = client,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DossierFacts(item: CatalogItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                MetaChip("MATURE", accent = YomiraColors.Lavender)
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DossierActions(
    item: CatalogItem,
    onRead: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
}

@Composable
private fun DossierStatus(
    item: CatalogItem,
    loading: Boolean,
    message: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spinner(size = 13.dp)
                Text("Şelale birleştiriliyor…", color = YomiraColors.TextMuted, fontSize = 11.sp)
            }
        }
        message?.takeIf { !item.isReadableOnDesktop }?.let {
            Text(it, color = YomiraColors.Gold, fontSize = 11.5.sp, lineHeight = 17.sp)
        }
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
            .clip(YomiraShape.Control)
            .background(YomiraColors.Surface.copy(alpha = 0.72f))
            .border(1.dp, YomiraColors.Outline, YomiraShape.Control)
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
