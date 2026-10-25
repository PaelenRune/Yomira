package com.yomira.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.WesternComics
import com.yomira.core.ui.*

/** Apply at every presentation boundary, including search and curated rails. */
fun publicCatalog(items: List<Manga>): List<Manga> = items.filter { it.contentRating != ContentRating.EXPLICIT }

data class HomeUiState(
    val manga: List<Manga> = emptyList(),
    val trending: List<Manga> = emptyList(),
    val updated: List<Manga> = emptyList(),
    val columns: Int = 3,
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val searching: Boolean = false,
    val searchMessage: String? = null,
    /** Selected tag ids of the 30+ tag matrix; AND-combined, see [filterByTags]. */
    val selectedTags: List<String> = emptyList(),
    /** Append-only pages fetched by the endless-scroll engine. */
    val discovery: List<Manga> = emptyList(),
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val pagingError: String? = null,
)

/**
 * All user-facing texts of the home catalog. The app module builds one from string
 * resources (English default, Turkish in `values-tr`), so an in-app locale switch
 * re-renders this screen instantly. Defaults keep previews and tests stable.
 */
data class HomeCopy(
    val searchPlaceholder: String = "Seri veya tür ara",
    val yourShelf: String = "Senin vitrinin",
    val retry: String = "Yeniden dene",
    val railTrending: String = "Trendler",
    val railUpdated: String = "Son güncellenenler",
    val emptyShelfTitle: String = "Vitrinin henüz boş",
    val emptyShelfMessage: String = "Bir kaynak ekleyip seri meta verilerini eşitlediğinde serilerin burada görünür.",
    val emptySearchTitle: String = "Sonuç bulunamadı",
    val emptySearchMessage: String = "Başka bir seri adı veya tür dene.",
    val tagsHeading: String = "Etiketlerle daralt",
    val clearTags: String = "Filtreleri temizle",
    val pagingLoading: String = "Daha fazla seri yükleniyor",
    val pagingEnd: String = "Tüm seriler yüklendi · %1\$d seri",
    val catalog: CatalogCopy = CatalogCopy(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQuery: (String) -> Unit,
    onColumns: (Int) -> Unit,
    onManga: (Manga) -> Unit,
    onRetry: () -> Unit,
    title: String = "Yomira",
    showHero: Boolean = true,
    tagOptions: List<TagChip> = emptyList(),
    onToggleTag: (String) -> Unit = {},
    onClearTags: () -> Unit = {},
    copy: HomeCopy = HomeCopy(),
    onLoadMore: () -> Unit = {},
    /** Width bucket of the host window; phones get one column, tablets expand the grid. */
    sizeClass: WindowSizeClass = WindowSizeClass.Medium,
) {
    val columns = AdaptiveLayout.catalogColumns(sizeClass, state.columns)
    // Room re-emits every discovered row. Appending the page list after the
    // Room snapshot keeps newly fetched cards at the end without duplicating
    // a series that is already on screen.
    val all = publicCatalog(state.manga + state.discovery).distinctBy { it.id }
    val visible = all.filter {
        it.title.contains(state.query, ignoreCase = true) ||
            it.altTitles.any { alt -> alt.contains(state.query, ignoreCase = true) } ||
            it.genres.any { genre -> genre.contains(state.query, true) }
    }.let { filtered -> filterByTags(filtered, state.selectedTags, matchAll = true) }
    val westernSpotlight = remember(all) { WesternComics.spotlight(all, limit = 14) }
    val gridState = rememberLazyGridState()
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearBottom, state.loadingMore, state.hasMore) {
        if (nearBottom && state.hasMore && !state.loadingMore) onLoadMore()
    }
    LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columns), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                OutlinedTextField(value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text(copy.searchPlaceholder) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                state.searchMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(copy.yourShelf, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    // A phone is intentionally single-column; showing a 2/3/4 selector
                    // there would advertise a layout that cannot be applied.
                    if (sizeClass != WindowSizeClass.Compact) {
                        GridColumnSelector(columns, onColumns, copy = copy.catalog)
                    }                }
            }
        }
        if (tagOptions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.query.isBlank()) {
                        Text(
                            copy.tagsHeading,
                            Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MultiTagFilterBar(
                        tags = tagOptions,
                        selected = state.selectedTags,
                        onToggle = onToggleTag,
                        onClear = onClearTags,
                        clearLabel = copy.clearTags,
                    )
                }
            }
        }
        if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(20.dp)) { Text(message); TextButton(onClick = onRetry) { Text(copy.retry) } }
        } }
        if (showHero && state.query.isBlank() && state.selectedTags.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { HeroCarousel(publicCatalog(state.trending).take(6), onManga, copy = copy.catalog) }
            item(span = { GridItemSpan(maxLineSpan) }) { WesternSpotlightRail(westernSpotlight, onManga, copy.catalog) }
            item(span = { GridItemSpan(maxLineSpan) }) { MangaRail(copy.railTrending, publicCatalog(state.trending), onManga, copy.catalog) }
            item(span = { GridItemSpan(maxLineSpan) }) { MangaRail(copy.railUpdated, publicCatalog(state.updated), onManga, copy.catalog) }
        }
        if (visible.isEmpty() && !state.loading && state.error == null) item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogEmptyState(
                if (state.query.isBlank()) copy.emptyShelfTitle else copy.emptySearchTitle,
                if (state.query.isBlank()) copy.emptyShelfMessage else copy.emptySearchMessage,
            )
        }
        items(visible, key = { it.id }) { manga ->
            MangaCard(manga, columns, { onManga(manga) }, Modifier.padding(horizontal = 4.dp), copy.catalog)
        }
        if (state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) { NeonPagingIndicator(message = copy.pagingLoading) }
        } else if (state.pagingError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(state.pagingError, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onLoadMore) { Text(copy.retry) }
                }
            }
        } else if (!state.hasMore && state.discovery.isNotEmpty()) {
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

/**
 * Editorial western-comics strip. It is drawn from persisted seed rows first,
 * then from ComicVine / OpenLibrary / MangaDex(en) records discovered by later
 * pages, so the rail stays useful offline and grows online.
 */
@Composable
private fun WesternSpotlightRail(manga: List<Manga>, onManga: (Manga) -> Unit, copy: CatalogCopy) {
    if (manga.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(WesternComics.SPOTLIGHT_TITLE, style = MaterialTheme.typography.titleLarge)
            Text(WesternComics.SPOTLIGHT_SUBTITLE, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(manga, key = { it.id }) { item -> MangaCard(item, 3, { onManga(item) }, Modifier.width(128.dp), copy) }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 6

@Composable
private fun MangaRail(title: String, manga: List<Manga>, onManga: (Manga) -> Unit, copy: CatalogCopy = CatalogCopy()) {
    if (manga.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(manga, key = { it.id }) { item -> MangaCard(item, 3, { onManga(item) }, Modifier.width(128.dp), copy) }
        }
    }
}
