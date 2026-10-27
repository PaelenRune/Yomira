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
import com.yomira.core.model.HomeRail
import com.yomira.core.model.HomeShelf
import com.yomira.core.ui.*
import java.util.Locale
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
@Immutable
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

/** Collection properties are immutable snapshots; publish a new state on changes. */
@Immutable
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
    /** Ids already opened in the reader; drives the personalized rail. */
    val readIds: Set<String> = emptySet(),
    /** Ids the user keeps in the library; a second, stronger taste signal. */
    val savedIds: Set<String> = emptySet(),
    val rails: List<HomeRail> = emptyList(),
)

/**
 * All user-facing texts of the home catalog. The app module builds one from string
 * resources (English default, Turkish in `values-tr`), so an in-app locale switch
 * re-renders this screen instantly. Defaults keep previews and tests stable.
 */
@Immutable
data class HomeCopy(
    val searchPlaceholder: String = "Seri veya tür ara",
    val subtitle: String = "Metadata kütüphanen. Okuma deneyimin.",
    val yourShelf: String = "Senin vitrinin",
    val retry: String = "Yeniden dene",
    val railTrending: String = "Trendler",
    val railUpdated: String = "Son güncellenenler",
    val railPopular: String = "Popüler Seriler",
    val railPersonal: String = "Size Özel",
    val railIsekai: String = "🌀 Popüler Isekai & Fantezi",
    val railEditors: String = "🏆 Editörün Seçtikleri",
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
    val shelfTitles: Map<HomeShelf, String> = emptyMap(),
    val emptyRail: String = "Bu rafta henüz seri yok.",
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
    val all = remember(state.manga, state.discovery) {
        publicCatalog(state.manga + state.discovery).distinctBy { it.id }
    }
    val visible = remember(all, state.query, state.selectedTags) {
        all.filter {
            it.title.contains(state.query, ignoreCase = true) ||
                it.altTitles.any { alt -> alt.contains(state.query, ignoreCase = true) } ||
                it.genres.any { genre -> genre.contains(state.query, true) }
        }.let { filtered -> filterByTags(filtered, state.selectedTags, matchAll = true) }
    }
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
        item(key = "home-header", contentType = "header", span = { GridItemSpan(maxLineSpan) }) {
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
            item(key = "continue-reading", contentType = "resume", span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingCard(
                    item = continueReading,
                    copy = copy,
                    onClick = { onContinueReading(continueReading) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        if (tagOptions.isNotEmpty()) {
            item(key = "tag-filter", contentType = "filters", span = { GridItemSpan(maxLineSpan) }) {
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
        if (state.loading) item(key = "loading", contentType = "loading", span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item(key = "error", contentType = "error", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(20.dp)) { Text(message); TextButton(onClick = onRetry) { Text(copy.retry) } }
        } }
        if (showHero && quietShelf) {
            item(key = "hero", contentType = "hero", span = { GridItemSpan(maxLineSpan) }) { HeroCarousel(publicCatalog(state.trending).take(6), onManga, copy = copy.catalog) }
            state.rails.forEach { rail ->
                item(key = "shelf-${rail.shelf.name}", contentType = "shelf", span = { GridItemSpan(maxLineSpan) }) {
                    val railTitle = copy.shelfTitles[rail.shelf] ?: when (rail.shelf) {
                        HomeShelf.POPULAR -> copy.railPopular
                        HomeShelf.PERSONAL -> copy.railPersonal
                        else -> rail.shelf.title
                    }
                    MangaRail(railTitle, rail.items, onManga, copy.catalog, copy.emptyRail)
                }
            }
        }
        if (visible.isEmpty() && !state.loading && state.error == null) item(key = "empty", contentType = "empty", span = { GridItemSpan(maxLineSpan) }) {
            CatalogEmptyState(
                if (state.query.isBlank()) copy.emptyShelfTitle else copy.emptySearchTitle,
                if (state.query.isBlank()) copy.emptyShelfMessage else copy.emptySearchMessage,
            )
        }
        items(visible, key = { it.id }, contentType = { "manga" }) { manga ->
            MangaCard(manga, columns, { onManga(manga) }, Modifier.padding(horizontal = 4.dp), copy.catalog)
        }
        if (state.loadingMore) {
            item(key = "paging-loading", contentType = "loading", span = { GridItemSpan(maxLineSpan) }) { NeonPagingIndicator(message = copy.pagingLoading) }
        } else if (state.pagingError != null) {
            item(key = "paging-error", contentType = "error", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.pagingError, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    TextButton(onClick = onLoadMore) { Text(copy.retry) }
                }
            }
        } else if (!state.hasMore && state.discovery.isNotEmpty()) {
            item(key = "paging-end", contentType = "paging-end", span = { GridItemSpan(maxLineSpan) }) {
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
                    .aspectRatio(PosterAspectRatio)
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

/** Score used when a provider has not published one, so ranking never drops a series. */
private const val FALLBACK_SCORE = 6.8

/**
 * Deterministic 0..1 noise. The same series id and rotation seed always produce
 * the same value, so a recomposition never reorders a shelf mid-scroll; only the
 * next explicit refresh does.
 */
internal fun rotationNoise(key: String, seed: Int): Double {
    var hash = 7
    key.forEach { char -> hash = hash * 31 + char.code }
    hash = hash * 31 + seed
    return ((hash.toLong() and 0x7FFF_FFFFL) % 1000L) / 1000.0
}

/**
 * "🔥 Popülerler & Trendler": score and freshness driven, with a small rotating
 * tie-break so the order feels alive rather than frozen. Recently updated rows
 * get a bonus, which lets new releases climb next to the established hits.
 */
internal fun popularTrendingShelf(
    all: List<Manga>,
    updated: List<Manga>,
    seed: Int,
    limit: Int = 14,
): List<Manga> {
    if (all.isEmpty()) return emptyList()
    val denominator = updated.size.coerceAtLeast(1)
    val freshness = updated.mapIndexed { index, item ->
        item.id to (1.0 - index.toDouble() / denominator)
    }.toMap()
    return all.distinctBy { it.id }
        .sortedWith(
            compareByDescending<Manga> { item ->
                val score = (item.score ?: FALLBACK_SCORE) / 10.0
                val fresh = freshness[item.id] ?: 0.0
                score * .58 + fresh * .27 + rotationNoise(item.id, seed) * .15
            }.thenBy { it.id },
        )
        .take(limit)
}

/**
 * "✨ Sana Özel Öneriler": builds a genre taste profile from the series the user
 * has opened or saved, then recommends unread series weighted by that affinity.
 * On a cold start (no history) it falls back to a high-score discovery mix, so
 * the rail is never empty.
 */
internal fun personalizedShelf(
    all: List<Manga>,
    readIds: Set<String>,
    savedIds: Set<String>,
    seed: Int,
    limit: Int = 14,
): List<Manga> {
    if (all.isEmpty()) return emptyList()
    val tasteIds = readIds + savedIds
    val favoriteGenres = all.asSequence()
        .filter { it.id in tasteIds }
        .flatMap { it.genres.asSequence() }
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
    val strongest = favoriteGenres.values.maxOrNull() ?: 0
    val hasTaste = strongest > 0
    return all.distinctBy { it.id }
        .filter { it.id !in readIds }
        .sortedWith(
            compareByDescending<Manga> { item ->
                val score = (item.score ?: FALLBACK_SCORE) / 10.0
                val affinity = if (!hasTaste) {
                    0.0
                } else {
                    (item.genres.sumOf { genre ->
                        (favoriteGenres[genre.trim().lowercase(Locale.ROOT)] ?: 0).toDouble() / strongest
                    }.coerceAtMost(2.0)) / 2.0
                }
                val noise = rotationNoise(item.id, seed + 41)
                // Without history the rail must still look curated, so score
                // dominates; with history, genre affinity takes the lead.
                if (hasTaste) affinity * .55 + score * .32 + noise * .13
                else score * .78 + noise * .22
            }.thenBy { it.id },
        )
        .take(limit)
}

/** "🌀 Popüler Isekai & Fantezi": isekai/reincarnation first, then the fantasy shelf. */
internal fun isekaiFantasyShelf(all: List<Manga>, seed: Int, limit: Int = 14): List<Manga> {
    return all.distinctBy { it.id }
        .map { item ->
            val tags = item.genres.map { it.trim().lowercase(Locale.ROOT) }
            val isekai = tags.any { tag -> ISEKAI_GENRES.any { tag.contains(it) } }
            val fantasy = tags.any { tag -> FANTASY_GENRES.any { tag.contains(it) } }
            Triple(item, isekai, fantasy)
        }
        .filter { (_, isekai, fantasy) -> isekai || fantasy }
        .sortedWith(
            compareByDescending<Triple<Manga, Boolean, Boolean>> { (item, isekai, _) ->
                val isekaiBonus = if (isekai) .26 else 0.0
                val score = (item.score ?: FALLBACK_SCORE) / 10.0
                isekaiBonus + score * .57 + rotationNoise(item.id, seed + 97) * .17
            }.thenBy { it.first.id },
        )
        .take(limit)
        .map { it.first }
}

/**
 * Genre needles of the isekai shelf. They are plain `contains` checks on already
 * lowercased genre strings: the shelf is built for every item of the catalogue on
 * the composition thread, and `CatalogTags.tagIdsFor` compiles a regex per call,
 * which previously froze the UI for seconds on a large catalogue.
 */
private val ISEKAI_GENRES = listOf(
    "isekai", "reincarnation", "another world", "parallel world", "game world", "time travel",
)
private val FANTASY_GENRES = listOf("fantasy", "fantastik")

/**
 * "🏆 Editörün Seçtikleri": the highest-rated shelf. Only elite records compete
 * when there are enough of them; the rotation picks which slice of that elite
 * pool leads the rail each cycle.
 */
internal fun editorsChoiceShelf(all: List<Manga>, seed: Int, limit: Int = 14): List<Manga> {
    val ranked = all.distinctBy { it.id }
        .filter { it.score != null }
        .sortedWith(compareByDescending<Manga> { it.score ?: 0.0 }.thenBy { it.id })
    val elite = ranked.filter { (it.score ?: 0.0) >= 8.0 }
    val pool = when {
        elite.size >= limit.coerceAtMost(MIN_EDITORS_POOL) -> elite
        ranked.isNotEmpty() -> ranked
        else -> return popularTrendingShelf(all, emptyList(), seed, limit)
    }
    val offset = if (pool.size > 1) Math.floorMod(seed, pool.size) else 0
    return (pool.drop(offset) + pool.take(offset)).take(limit)
}

private const val MIN_EDITORS_POOL = 6
private const val LOAD_MORE_THRESHOLD = 6

@Composable
private fun MangaRail(title: String, manga: List<Manga>, onManga: (Manga) -> Unit, copy: CatalogCopy, emptyMessage: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = 20.dp))
        if (manga.isEmpty()) {
            Text(emptyMessage, Modifier.padding(horizontal = 20.dp), color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(manga, key = { it.id }, contentType = { "manga" }) { item -> MangaCard(item, 3, { onManga(item) }, Modifier.width(128.dp), copy) }
        }
    }
}
