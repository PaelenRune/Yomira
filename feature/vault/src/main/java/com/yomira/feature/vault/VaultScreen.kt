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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(unlocked: Boolean, manga: List<Manga>, columns: Int, onColumns: (Int) -> Unit, onUnlock: () -> Unit, onPanic: () -> Unit, onManga: (Manga) -> Unit, authError: String? = null, loading: Boolean = false, error: String? = null, onRetry: () -> Unit) {
    if (!unlocked) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = NeonFuchsia)
            Text("Sadece sana ait", style = MaterialTheme.typography.headlineLarge)
            Text("+18 Vault ayrı ve kilitli bir vitrindir. Devam ederek 18 yaşından büyük olduğunu onaylarsın.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Cihaz PIN’i, desen, şifre veya biyometri ile doğrula. Uygulama arka plana geçtiğinde kasa yeniden kilitlenir.", style = MaterialTheme.typography.bodyMedium)
            authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onUnlock) { Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("18 yaşındayım · Kilidi aç") }
            TextButton(onClick = onPanic) { Text("Ana sayfaya dön") }
        }
        return
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val gridColumns = columns.coerceIn(2, 4)
    val adult = vaultCatalog(manga, unlocked)
    val visible = vaultCatalog(manga, unlocked, selected)
    LazyVerticalGrid(columns = GridCells.Fixed(gridColumns), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vault", style = MaterialTheme.typography.headlineLarge, color = NeonFuchsia)
                FilledTonalButton(onClick = onPanic) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Hızlı kilitle") }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { HeroCarousel(adult.sortedByDescending { it.score }.take(6), onManga, vault = true) }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonFuchsia) }
        error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(20.dp)) { Text(message); TextButton(onClick = onRetry) { Text("Yeniden dene") } }
        } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected == null, { selected = null }, label = { Text("Tümü") }) }
                items(listOf("Adult Webtoons", "Hentai Manga", "Smut", "Doujinshi")) { genre ->
                    FilterChip(selected == genre, { selected = if (selected == genre) null else genre }, label = { Text(genre) })
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Özel koleksiyon", style = MaterialTheme.typography.titleLarge)
                GridColumnSelector(gridColumns, onColumns)
            }
        }
        if (visible.isEmpty() && !loading && error == null) item(span = { GridItemSpan(maxLineSpan) }) { CatalogEmptyState("Bu vitrin boş", "Seçili türde yetişkin serisi bulunmuyor. Kaynaklarını eşitle veya filtreyi değiştir.") }
        items(visible, key = { it.id }) { item -> MangaCard(item, gridColumns, { onManga(item) }, Modifier.padding(horizontal = 4.dp)) }
    }
}
