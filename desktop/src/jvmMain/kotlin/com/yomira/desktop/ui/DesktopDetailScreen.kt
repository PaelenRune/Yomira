package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.core.network.MetadataSources
import com.yomira.desktop.data.openInBrowser
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.model.desktopLabel
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.heroItem
import okhttp3.OkHttpClient

/**
 * Series dossier with the master-detail geometry the same product uses on a
 * tablet: the chapter spine lives in a fixed left pane, the cover, credits and
 * synopsis fill the remaining width.
 */
@Composable
fun DesktopDetailScreen(state: DesktopAppState, client: OkHttpClient, modifier: Modifier = Modifier) {
    val item = state.heroItem()
    if (item == null) {
        LaunchedEffectClose { state.closeDetail() }
        return
    }
    Column(modifier.fillMaxSize().background(YomiraColors.Canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = state::closeDetail) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = DesktopCopy.BACK, tint = YomiraColors.Ink)
            }
            Text(
                item.title,
                color = YomiraColors.Ink,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                DesktopCopy.CHAPTERS + " · " + state.chapterList.size,
                color = YomiraColors.InkMuted,
                fontSize = 12.sp,
            )
            IconButton(onClick = state::closeDetail) {
                Icon(Icons.Default.Close, contentDescription = DesktopCopy.CLOSE, tint = YomiraColors.InkMuted)
            }
        }
        Row(Modifier.weight(1f)) {
            ChapterPane(
                state = state,
                item = item,
                modifier = Modifier.width(360.dp).fillMaxHeight(),
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(YomiraColors.Hairline))
            DossierPane(
                item = item,
                client = client,
                loading = state.detailLoading,
                message = state.detailError,
                onRead = {
                    state.chapterList.firstOrNull(DesktopChapter::isReadable)?.let(state::openChapter)
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ChapterPane(state: DesktopAppState, item: CatalogItem, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.List, null, tint = YomiraColors.Violet)
            Text(
                DesktopCopy.CHAPTERS,
                color = YomiraColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            if (item.isReadableOnDesktop) InfoChip(com.yomira.desktop.data.MangaDexChapterRepository.LANGUAGE_LABEL)
        }
        val message = state.chaptersMessage
        when {
            state.chaptersLoading -> LoadingBlock(DesktopCopy.CHAPTERS_LOADING, Modifier.fillMaxWidth())
            message != null -> MessageBlock(message, Modifier.fillMaxWidth())
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.chapterList.reversed(), key = { it.id }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        onOpen = {
                            if (chapter.isReadable) state.openChapter(chapter)
                            else openInBrowser(chapter.externalUrl)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: DesktopChapter, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                chapter.displayLabel,
                color = YomiraColors.Ink,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Text(
                buildList {
                    chapter.group?.let { add(it) }
                    if (chapter.pageCount > 0) add(DesktopCopy.CHAPTER_PAGES.format(chapter.pageCount))
                    if (!chapter.isReadable) add(DesktopCopy.EXTERNAL_CHAPTER)
                }.joinToString(" · "),
                color = YomiraColors.InkMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = if (chapter.isReadable) DesktopCopy.READ else DesktopCopy.OPEN_EXTERNAL,
            tint = if (chapter.isReadable) YomiraColors.Violet else YomiraColors.InkMuted,
            modifier = Modifier.size(18.dp),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (item.bannerUrl != null) {
            RemoteImage(
                url = item.bannerUrl,
                client = client,
                key = "banner:${item.id}",
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                    .background(YomiraColors.Panel),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                Modifier
                    .size(width = 170.dp, height = 248.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(YomiraColors.Panel)
                    .border(1.dp, YomiraColors.Hairline, RoundedCornerShape(12.dp)),
            ) {
                RemoteImage(
                    url = item.coverUrl,
                    client = client,
                    key = item.coverKey,
                    contentDescription = item.title,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, color = YomiraColors.Ink, style = MaterialTheme.typography.headlineSmall)
                item.author?.let {
                    Text("${DesktopCopy.AUTHOR}: $it", color = YomiraColors.InkMuted, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    item.year?.let { InfoChip(it.toString()) }
                    item.status.desktopLabel()?.let { InfoChip(it) }
                    ScoreBadge(item.score)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.genres.take(8).forEach { InfoChip(it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRead,
                        enabled = item.isReadableOnDesktop,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = YomiraColors.Violet),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Text("  ${DesktopCopy.READ}", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { openInBrowser(item.sourceUrl ?: defaultSourceUrl(item)) },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(15.dp), tint = YomiraColors.InkMuted)
                        Text("  ${DesktopCopy.OPEN_SOURCE}", fontSize = 13.sp, color = YomiraColors.InkMuted)
                    }
                }
                if (loading) Text(DesktopCopy.LOADING, color = YomiraColors.InkMuted, fontSize = 11.sp)
                message?.let { Text(it, color = YomiraColors.Amber, fontSize = 12.sp) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                DesktopCopy.SYNOPSIS,
                color = YomiraColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                item.synopsis ?: DesktopCopy.SEARCH_EMPTY,
                color = YomiraColors.InkMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        Text(
            DesktopCopy.POWERED_BY_MANGADEX,
            color = YomiraColors.InkMuted.copy(alpha = 0.6f),
            fontSize = 10.sp,
        )
    }
}

/** Canonical provider page so "open source" works even for cards without a URL. */
private fun defaultSourceUrl(item: CatalogItem): String? = when (item.sourceId) {
    MetadataSources.MANGADEX -> "https://mangadex.org/title/${item.remoteId}"
    MetadataSources.ANILIST -> "https://anilist.co/manga/${item.remoteId}"
    MetadataSources.BAKA_UPDATES -> "https://www.mangaupdates.com/series.html?id=${item.remoteId}"
    MetadataSources.KITSU -> "https://kitsu.io/manga/${item.remoteId}"
    else -> item.sourceUrl
}

/** Runs [block] once, for "close me" effects in `null` branches of a screen. */
@Composable
private fun LaunchedEffectClose(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
