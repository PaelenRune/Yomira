package com.yomira.feature.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.Normalizer
import java.util.Locale

/**
 * The four distinct Vault shelves. Chips only ever filter [ContentRating.EXPLICIT]
 * titles, so a mature-but-soft series can never appear under any of them.
 */
val vaultGenreChips: List<String> = listOf("Adult Webtoons", "Hentai Manga", "Doujinshi", "Western / 3D")

fun vaultCatalog(manga: List<Manga>, unlocked: Boolean, genre: String? = null): List<Manga> {
    if (!unlocked) return emptyList()
    val selectedGenre = genre?.let(::normalizeGenre)
    val aliases = when (selectedGenre) {
        // Korean adult webtoons and other vertical-scroll adult comics.
        "adult webtoons" -> setOf("adult webtoon", "adult webtoons", "webtoon", "webtoons", "manhwa", "pornhwa", "adult comic", "adult comics")
        "hentai manga" -> setOf("hentai manga", "hentai")
        // Preserve callers using the former chip without showing it as a category.
        "smut" -> setOf("smut", "erotica", "erotic")
        // Western erotica and 3D-rendered adult comics, including everything scraped
        // from allporncomic-type platforms whose tags survive verbatim.
        "western 3d" -> setOf(
            "western erotica", "western erotic", "erotic comics", "erotic comic",
            "3d comic", "3d comics", "3d comix", "3d porn", "3dcg porn", "3d hentai",
            "allporncomic", "allporncomics", "all porn comic", "all porn comics", "porn comic", "porn comics", "porncomix", "porcomix", "western porn",
        )
        "doujinshi" -> setOf("doujinshi", "doujin", "doujinshis", "doujins", "dōjinshi", "dōjin")
        else -> emptySet()
    }
    return manga.filter { item ->
        // Source identity also identifies AllPornComic imports with missing genre metadata.
        val labels = if (selectedGenre == "western 3d") item.genres + item.sourceId else item.genres
        item.contentRating == ContentRating.EXPLICIT && (genre == null || labels.any { label ->
            // Providers flatten tags into genres, sometimes as a single delimited string.
            // Word boundaries allow "Hentai Doujinshi" but never "Nonhentai".
            val normalized = " ${normalizeGenre(label)} "
            aliases.any { alias -> " $alias " in normalized }
        })
    }
}

private fun normalizeGenre(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

internal fun toggleVaultGenre(selected: String?, clicked: String): String? =
    if (selected == clicked) null else clicked

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
    val badge: String = "+18",
    val railTrendingWebtoons: String = "🔥 Trending Adult Webtoons",
    val railHentaiManga: String = "🌸 Hentai Manga & Classics",
    val railNewDoujinshi: String = "⚡ New Doujinshi",
    val railWestern3D: String = "🌐 Western & 3D Erotic Comics",
    val railPopular: String = "Popular in Vault",
    val emptyRail: String = "No series on this shelf yet. Sync your sources or change the filter.",
) {
    fun railTitle(style: String): String = when (style) {
        "Adult Webtoons" -> railTrendingWebtoons
        "Hentai Manga" -> railHentaiManga
        "Doujinshi" -> railNewDoujinshi
        "Western / 3D" -> railWestern3D
        else -> railPopular
    }
}

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
    val home = remember(manga, discovered, unlocked, selected) {
        buildVaultHome(manga, discovered, unlocked, selected)
    }
    val visible = home.collection
    val gridState = rememberLazyGridState()
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(unlocked) { if (unlocked) onLoadMore() }
    LaunchedEffect(nearBottom, loadingMore, hasMore, pagingError, discovered.size) {
        if (nearBottom && hasMore && !loadingMore && pagingError == null) onLoadMore()
    }
    LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(gridColumns), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.heading, style = MaterialTheme.typography.headlineLarge, color = NeonFuchsia)
                Surface(shape = RoundedCornerShape(8.dp), color = NeonFuchsia.copy(alpha = .14f), contentColor = NeonFuchsia) {
                    Text(copy.badge, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(onClick = onPanic) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(copy.lockFast) }
            }
        }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonFuchsia) }
        error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(20.dp)) { Text(message); TextButton(onClick = onRetry) { Text(copy.retry) } }
        } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected == null, { selected = null }, label = { Text(copy.filterAll) }) }
                items(vaultGenreChips) { genre ->
                    FilterChip(selected == genre, { selected = toggleVaultGenre(selected, genre) }, label = { Text(genre) })
                }
            }
        }
        if (home.hero.isNotEmpty()) {
            item(key = "vault-hero", span = { GridItemSpan(maxLineSpan) }) {
                HeroCarousel(home.hero, onManga, vault = true, copy = copy.catalog)
            }
        }
        home.rails.forEach { rail ->
            item(key = "vault-rail-${rail.style}", contentType = "rail", span = { GridItemSpan(maxLineSpan) }) {
                VaultMangaRail(rail, onManga, copy)
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

@Composable
private fun VaultMangaRail(rail: VaultRail, onManga: (Manga) -> Unit, copy: VaultCopy) {
    val title = copy.railTitle(rail.style)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        if (rail.items.isEmpty()) {
            Text(copy.emptyRail, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rail.items, key = { it.id }, contentType = { "manga" }) { item ->
                    MangaCard(item, 3, { onManga(item) }, Modifier.width(128.dp), copy.catalog)
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 6
