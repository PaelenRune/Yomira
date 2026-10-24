package com.yomira.feature.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.NeonFuchsia

fun vaultCatalog(manga: List<Manga>, unlocked: Boolean, genre: String? = null): List<Manga> {
    if (!unlocked) return emptyList()
    val aliases = when (genre) {
        "Adult Webtoons" -> setOf("adult webtoons", "webtoon", "webtoons", "manhwa")
        "Hentai Manga" -> setOf("hentai manga", "hentai")
        "Smut" -> setOf("smut")
        "Doujinshi" -> setOf("doujinshi", "doujin")
        else -> emptySet()
    }
    return manga.filter { item -> item.contentRating == ContentRating.EXPLICIT && (genre == null || item.genres.any { it.lowercase(java.util.Locale.ROOT) in aliases }) }
}

/** Localized texts of the +18 Vault shelf. */
data class VaultCopy(
    val heading: String = "Vault",
    val lockedTitle: String = "Sadece sana ait",
    val lockedBody: String = "+18 Vault ayrı ve kilitli bir vitrindir. Devam ederek 18 yaşından büyük olduğunu onaylarsın.",
    val lockedHint: String = "Cihaz PIN’i, desen, şifre veya biyometri ile doğrula. Uygulama arka plana geçtiğinde kasa yeniden kilitlenir.",
    val unlockButton: String = "18 yaşındayım · Kilidi aç",
    val backHome: String = "Ana sayfaya dön",
    val lockFast: String = "Hızlı kilitle",
    val retry: String = "Yeniden dene",
    val filterAll: String = "Tümü",
    val collectionTitle: String = "Özel koleksiyon",
    val emptyTitle: String = "Bu vitrin boş",
    val emptyMessage: String = "Seçili türde yetişkin serisi bulunmuyor. Kaynaklarını eşitle veya filtreyi değiştir.",
    val pagingLoading: String = "Daha fazla yetişkin serisi yükleniyor",
    val pagingEnd: String = "Tüm yetişkin serileri yüklendi · %1\$d seri",
    val catalog: CatalogCopy = CatalogCopy(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(unlocked: Boolean, manga: List<Manga>, columns: Int, onColumns: (Int) -> Unit, onUnlock: () -> Unit, onPanic: () -> Unit, onManga: (Manga) -> Unit, authError: String? = null, loading: Boolean = false, error: String? = null, onRetry: () -> Unit, discovered: List<Manga> = emptyList(), loadingMore: Boolean = false, hasMore: Boolean = true, pagingError: String? = null, onLoadMore: () -> Unit = {}, copy: VaultCopy = VaultCopy()) {
    if (!unlocked) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = NeonFuchsia)
            Text(copy.lockedTitle, style = MaterialTheme.typography.headlineLarge)
            Text(copy.lockedBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(copy.lockedHint, style = MaterialTheme.typography.bodyMedium)
            authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onUnlock) { Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text(copy.unlockButton) }
            TextButton(onClick = onPanic) { Text(copy.backHome) }
        }
        return
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val gridColumns = columns.coerceIn(2, 4)
    // Room and the append-only page list are merged by id; the Vault never sees
    // public rows because vaultCatalog() filters EXPLICIT for every input.
    val all = (manga + discovered).distinctBy { it.id }
    val adult = vaultCatalog(all, unlocked)
    val visible = vaultCatalog(all, unlocked, selected)
    val gridState = rememberLazyGridState()
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(unlocked) { if (unlocked) onLoadMore() }
    LaunchedEffect(nearBottom, loadingMore, hasMore) {
        if (nearBottom && hasMore && !loadingMore) onLoadMore()
    }
    LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(gridColumns), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.heading, style = MaterialTheme.typography.headlineLarge, color = NeonFuchsia)
                FilledTonalButton(onClick = onPanic) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(copy.lockFast) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { HeroCarousel(adult.sortedByDescending { it.score }.take(6), onManga, vault = true, copy = copy.catalog) }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonFuchsia) }
        error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(20.dp)) { Text(message); TextButton(onClick = onRetry) { Text(copy.retry) } }
        } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected == null, { selected = null }, label = { Text(copy.filterAll) }) }
                items(listOf("Adult Webtoons", "Hentai Manga", "Smut", "Doujinshi")) { genre ->
                    FilterChip(selected == genre, { selected = if (selected == genre) null else genre }, label = { Text(genre) })
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.collectionTitle, style = MaterialTheme.typography.titleLarge)
                GridColumnSelector(gridColumns, onColumns, copy = copy.catalog)
            }
        }
        if (visible.isEmpty() && !loading && error == null) item(span = { GridItemSpan(maxLineSpan) }) { CatalogEmptyState(copy.emptyTitle, copy.emptyMessage) }
        items(visible, key = { it.id }) { item -> MangaCard(item, gridColumns, { onManga(item) }, Modifier.padding(horizontal = 4.dp), copy.catalog) }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) { NeonPagingIndicator(accent = NeonFuchsia, message = copy.pagingLoading) }
        } else if (pagingError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pagingError, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onLoadMore) { Text(copy.retry) }
                }
            }
        } else if (!hasMore && discovered.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    String.format(copy.pagingEnd, visible.size),
                    Modifier.fillMaxWidth().padding(20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 6
