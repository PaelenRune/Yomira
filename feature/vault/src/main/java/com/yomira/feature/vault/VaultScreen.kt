package com.yomira.feature.vault

import androidx.compose.foundation.layout.*
import android.util.Log
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
import com.yomira.core.model.isVisibleForReader
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.NeonFuchsia
import java.text.Normalizer
import java.util.Locale

/**
 * The four distinct Vault shelves. Chips only ever filter [ContentRating.EXPLICIT]
 * titles, so a mature-but-soft series can never appear under any of them.
 */
val vaultGenreChips: List<String> = listOf("Adult Webtoons", "Hentai Manga", "Doujinshi", "Western / 3D")

fun vaultCatalog(manga: List<Manga>, unlocked: Boolean, genre: String? = null, excludedTags: Set<String> = emptySet(), isAdult: Boolean = true, allowAdultContent: Boolean = true): List<Manga> {
    if (!unlocked) return emptyList()
    val selectedGenre = genre?.let(::normalizeGenre)
    val aliases = when (selectedGenre) {
        // Korean adult webtoons and other vertical-scroll adult comics.
        "adult webtoons" -> setOf("adult webtoon", "adult webtoons", "webtoon", "webtoons", "manhwa", "pornhwa", "long strip", "longstrip", "vertical", "vertical scroll", "dikey okuma")
        "hentai manga" -> setOf("manga", "japanese manga")
        // Preserve callers using the former chip without showing it as a category.
        "smut" -> setOf("smut", "erotica", "erotic")
        // Western erotica and 3D-rendered adult comics, including everything scraped
        // from allporncomic-type platforms whose tags survive verbatim.
        "western 3d" -> setOf(
            "western erotica", "western erotic", "erotic comics", "erotic comic",
            "western comic", "western comics", "comic", "comics", "graphic novel",
            "3d", "3dcg", "3d cg", "cg set", "3d comic", "3d comics", "3d comix", "3d porn", "3dcg porn", "3d hentai",
            "allporncomic", "allporncomics", "all porn comic", "all porn comics", "porn comic", "porn comics", "porncomix", "porcomix", "western porn",
        )
        "doujinshi" -> setOf("doujinshi", "doujin", "doujinshis", "doujins", "dōjinshi", "dōjin")
        else -> emptySet()
    }
    return manga.filter { item ->
        val labels = item.genres + listOfNotNull(item.publicationType)
        fun has(vararg values: String): Boolean = labels.any { label ->
            val normalized = " ${normalizeGenre(label)} "
            values.any { " $it " in normalized }
        }
        val webtoon = has("webtoon", "webtoons", "manhwa", "pornhwa", "long strip", "longstrip", "vertical", "dikey okuma")
        val western = has("western", "comic", "comics", "comix", "graphic novel", "3d", "3dcg", "cg set", "allporncomic", "allporncomics", "all porn comic", "all porn comics", "porncomix", "porcomix") ||
            normalizeGenre(item.sourceId).split(' ').any { it in setOf("allporncomic", "allporncomics", "porncomix", "porcomix") }
        val doujin = has("doujinshi", "doujin", "doujinshis", "doujins", "dōjinshi", "dōjin")
        // Format conflicts beat broad adult tags. In particular, Hentai is often
        // applied to Korean webtoons and is never allowed to override their format.
        val matches = when (selectedGenre) {
            null -> true
            "adult webtoons" -> webtoon && !western && !doujin
            "hentai manga" -> !webtoon && !western && !doujin && !has("manhua") &&
                (item.publicationType?.let(::normalizeGenre)?.let { it in setOf("manga", "japanese manga") } ?: true) &&
                has(*aliases.toTypedArray())
            "doujinshi" -> doujin && !webtoon && !western
            "western 3d" -> western
            else -> has(*aliases.toTypedArray())
        }
        item.contentRating == ContentRating.EXPLICIT && matches && item.isVisibleForReader(excludedTags, isAdult, allowAdultContent)
    }
}

private val genreSeparators = Regex("[^\\p{L}\\p{N}]+")

private fun normalizeGenre(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(genreSeparators, " ").trim()

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
    /** v8.2.0: shown when the first-use adult-content consent was declined. */
    val hiddenTitle: String = "Yetişkin içerik gizli",
    val hiddenMessage: String = "Bu kasa yetişkin içerik içeriyor ve onayı reddedildiğin için raflar boş. Kararı Ayarlar’dan değiştirebilirsin; giriş her zamanki gibi kilitli kalır.",
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
    val sparseRail: String = "Bu raf yavaş doldu; daha fazla sonuç için kaynakları eşitle.",
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
fun VaultScreen(unlocked: Boolean, manga: List<Manga>, columns: Int, onColumns: (Int) -> Unit, onUnlock: () -> Unit, onPanic: () -> Unit, onManga: (Manga) -> Unit, authError: String? = null, loading: Boolean = false, error: String? = null, onRetry: () -> Unit, discovered: List<Manga> = emptyList(), loadingMore: Boolean = false, hasMore: Boolean = true, pagingError: String? = null, onLoadMore: () -> Unit = {}, copy: VaultCopy = VaultCopy(), onMangaLongClick: (Manga) -> Unit = {}, adaptationBadges: Map<String, String> = emptyMap(), onPosterVisible: (Manga) -> Unit = {}, excludedTags: Set<String> = emptySet(), isAdult: Boolean = true, allowAdultContent: Boolean = true, showAdultContent: Boolean = true, diagnosticInput: List<Manga> = manga + discovered) {
    val eligible = isAdult && allowAdultContent
    if (!eligible) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text("Güvenli mod etkin. İçerik tercihlerini ayarlardan değiştirebilirsin.")
            TextButton(onClick = onPanic) { Text(copy.backHome) }
        }
        return
    }
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
    // v8.2.0 consent flow: a declined first-use prompt keeps the shelves empty
    // and the content hidden; the entry itself stays unlocked-but-gated as usual.
    val contentVisible = unlocked && showAdultContent
    // Room and the append-only page list are merged by id; the Vault never sees
    // public rows because vaultCatalog() filters EXPLICIT for every input.
    val home = remember(manga, discovered, unlocked, selected, excludedTags, isAdult, allowAdultContent, showAdultContent) {
        if (contentVisible) buildVaultHome(
            vaultCatalog(manga, unlocked, excludedTags = excludedTags, isAdult = isAdult, allowAdultContent = allowAdultContent),
            vaultCatalog(discovered, unlocked, excludedTags = excludedTags, isAdult = isAdult, allowAdultContent = allowAdultContent),
            unlocked, selected, excludedTags = excludedTags,
        ) else buildVaultHome(emptyList(), emptyList(), unlocked, selected)
    }
    val visible = home.collection
    LaunchedEffect(diagnosticInput, excludedTags, contentVisible, isAdult, allowAdultContent) {
        if (contentVisible) {
            vaultRailDiagnostics(diagnosticInput, excludedTags, isAdult, allowAdultContent).forEach { Log.d("VaultDiagnostics", it) }
        }
    }
    val gridState = rememberLazyGridState()
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(unlocked, showAdultContent) { if (contentVisible) onLoadMore() }
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
        if (contentVisible && home.hero.isNotEmpty()) {
            item(key = "vault-hero", span = { GridItemSpan(maxLineSpan) }) {
                HeroCarousel(home.hero, onManga, vault = true, copy = copy.catalog)
            }
        }
        if (contentVisible) home.rails.forEach { rail ->
            item(key = "vault-rail-${rail.style}", contentType = "rail", span = { GridItemSpan(maxLineSpan) }) {
                VaultMangaRail(rail, onManga, copy, onMangaLongClick, adaptationBadges, onPosterVisible)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.collectionTitle, style = MaterialTheme.typography.titleLarge)
                GridColumnSelector(gridColumns, onColumns, copy = copy.catalog)
            }
        }
        if (!contentVisible) item(key = "vault-hidden-consent", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(copy.hiddenTitle, style = MaterialTheme.typography.titleMedium, color = NeonFuchsia)
                Text(copy.hiddenMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (visible.isEmpty() && !loading && !loadingMore && error == null && pagingError == null) item(span = { GridItemSpan(maxLineSpan) }) { CatalogEmptyState(copy.emptyTitle, copy.emptyMessage) }
        if (contentVisible) items(visible, key = { it.id }) { item ->
            LaunchedEffect(item.id) { onPosterVisible(item) }
            MangaCard(item, gridColumns, { onManga(item) }, Modifier.padding(horizontal = 4.dp), copy.catalog, onLongClick = { onMangaLongClick(item) }, adaptationBadge = adaptationBadges[item.id])
        }
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
private fun VaultMangaRail(rail: VaultRail, onManga: (Manga) -> Unit, copy: VaultCopy, onLongClick: (Manga) -> Unit, adaptationBadges: Map<String, String>, onPosterVisible: (Manga) -> Unit) {
    val title = copy.railTitle(rail.style)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        if (rail.items.isEmpty()) {
            Text(copy.emptyRail, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            if (rail.items.size <= 3) Text(copy.sparseRail, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rail.items, key = { it.id }, contentType = { "manga" }) { item ->
                    LaunchedEffect(item.id) { onPosterVisible(item) }
                    MangaCard(item, 3, { onManga(item) }, Modifier.width(128.dp), copy.catalog, onLongClick = { onLongClick(item) }, adaptationBadge = adaptationBadges[item.id])
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 6
