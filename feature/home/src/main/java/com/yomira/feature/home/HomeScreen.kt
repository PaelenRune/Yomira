package com.yomira.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.WesternComics
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.CardSurface
import com.yomira.core.ui.theme.HairlineBorder
import com.yomira.core.ui.theme.NeonLavender
import com.yomira.core.ui.theme.NeonPurple
import com.yomira.core.ui.theme.SurfaceHighest
import com.yomira.core.ui.theme.SurfaceRaised
import com.yomira.core.ui.theme.TextMuted
import com.yomira.core.ui.theme.TextPrimary

/** Apply at every presentation boundary, including search and curated rails. */
fun publicCatalog(items: List<Manga>): List<Manga> = items.filter { it.contentRating != ContentRating.EXPLICIT }

/**
 * The single "pick up where you left off" entry of the home shelf. It is a plain
 * UI model: the app maps the newest reader-progress row plus its series/chapter
 * into it, so the card never holds database types and previews stay trivial.
 */
data class ContinueReadingUi(
    val mangaId: String,
    val chapterId: String,
    val title: String,
    val coverUrl: String?,
    val chapterTitle: String,
    val pageIndex: Int,
    val pageCount: Int,
) {
    /** 0f..1f reading completion of the open chapter. */
    val fraction: Float
        get() = if (pageCount <= 0) 0f else ((pageIndex + 1).toFloat() / pageCount).coerceIn(0f, 1f)
}

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
    val subtitle: String = "Metadata kütüphanen. Okuma deneyimin.",
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
    val continueReading: String = "Okumaya devam et",
    val continueAction: String = "Devam et",
    val continueProgress: String = "Sayfa %1\$d / %2\$d",
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
    /** Newest open chapter, rendered as the floating resume capsule on Home. */
    continueReading: ContinueReadingUi? = null,
    onContinueReading: (ContinueReadingUi) -> Unit = {},
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
    val quietShelf = state.query.isBlank() && state.selectedTags.isEmpty()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HomeHeader(
                title = title,
                query = state.query,
                onQuery = onQuery,
                copy = copy,
                searching = state.searching,
                searchMessage = state.searchMessage,
                columns = columns,
                onColumns = onColumns,
                showColumnSelector = sizeClass != WindowSizeClass.Compact,
                shelfLabel = copy.yourShelf,
            )
        }
        if (showHero && quietShelf && continueReading != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingCard(
                    item = continueReading,
                    copy = copy,
                    onClick = { onContinueReading(continueReading) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
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
                            color = TextMuted,
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
        if (showHero && quietShelf) {
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
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.pagingError, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    TextButton(onClick = onLoadMore) { Text(copy.retry) }
                }
            }
        } else if (!state.hasMore && state.discovery.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    String.format(copy.pagingEnd, visible.size),
                    Modifier.fillMaxWidth().padding(20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Editorial header: brand line, a capsule search field and the shelf title with
 * the (tablet-only) grid density selector. The search is drawn as a raised
 * capsule instead of Material's square outlined field so the top of the shelf
 * already speaks the same soft-corner language as the cards.
 */
@Composable
private fun HomeHeader(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    copy: HomeCopy,
    searching: Boolean,
    searchMessage: String?,
    columns: Int,
    onColumns: (Int) -> Unit,
    showColumnSelector: Boolean,
    shelfLabel: String,
) {
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                copy.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
        SearchCapsule(query, onQuery, copy.searchPlaceholder)
        if (searching) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(50)),
                color = NeonPurple,
                trackColor = SurfaceRaised,
            )
        }
        searchMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(shelfLabel, modifier = Modifier.weight(1f))
            // A phone is intentionally single-column; showing a 2/3/4 selector
            // there would advertise a layout that cannot be applied.
            if (showColumnSelector) GridColumnSelector(columns, onColumns, copy = copy.catalog)
        }
    }
}

@Composable
private fun SearchCapsule(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val capsule = RoundedCornerShape(percent = 50)
    Surface(
        shape = capsule,
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        tonalElevation = 0.dp,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            shape = capsule,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = NeonLavender,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
        )
    }
}

/** Small lavender keyline before a section label — a quiet editorial signature. */
@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(NeonPurple, NeonLavender))),
        )
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Floating "keep reading" capsule (Cstation home shelf): soft plum gradient,
 * violet halo, a small cover, the chapter line and a violet → lavender progress
 * bar. Tapping anywhere resumes; the round action is a second, obvious affordance.
 */
@Composable
private fun ContinueReadingCard(
    item: ContinueReadingUi,
    copy: HomeCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    YomiraCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        glow = true,
        onClick = onClick,
    ) {
        Row(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            NeonPurple.copy(alpha = .16f),
                            CardSurface,
                            CardSurface,
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaArtwork(
                item.coverUrl,
                item.title,
                Modifier
                    .width(58.dp)
                    .aspectRatio(.68f)
                    .clip(RoundedCornerShape(12.dp)),
                copy.catalog,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    copy.continueReading.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonLavender,
                    maxLines = 1,
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.chapterTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                GradientProgress(fraction = item.fraction)
                Text(
                    String.format(copy.continueProgress, item.pageIndex + 1, item.pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = NeonPurple,
                tonalElevation = 0.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = copy.continueAction,
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Thin violet → lavender completion bar shared by the resume card and previews. */
@Composable
private fun GradientProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(SurfaceHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(NeonPurple, NeonLavender))),
        )
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
            Text(WesternComics.SPOTLIGHT_SUBTITLE, style = MaterialTheme.typography.labelMedium, color = TextMuted)
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
        SectionTitle(title, Modifier.padding(horizontal = 20.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(manga, key = { it.id }) { item -> MangaCard(item, 3, { onManga(item) }, Modifier.width(128.dp), copy) }
        }
    }
}
