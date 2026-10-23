package com.yomira.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
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
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: HomeUiState, onQuery: (String) -> Unit, onColumns: (Int) -> Unit, onManga: (Manga) -> Unit, onRetry: () -> Unit, title: String = "Yomira", showHero: Boolean = true) {
    val columns = state.columns.coerceIn(2, 4)
    val visible = publicCatalog(state.manga).filter {
        it.title.contains(state.query, ignoreCase = true) ||
            it.altTitles.any { alt -> alt.contains(state.query, ignoreCase = true) } ||
            it.genres.any { genre -> genre.contains(state.query, true) }
    }
    LazyVerticalGrid(columns = GridCells.Fixed(columns), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                OutlinedTextField(value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Seri veya tür ara") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                state.searchMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Senin vitrinin", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    GridColumnSelector(columns, onColumns)
                }
            }
        }
        if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(20.dp)) { Text(message); TextButton(onClick = onRetry) { Text("Yeniden dene") } }
        } }
        if (showHero && state.query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) { HeroCarousel(publicCatalog(state.trending).take(6), onManga) }
            item(span = { GridItemSpan(maxLineSpan) }) { MangaRail("Trendler", publicCatalog(state.trending), onManga) }
            item(span = { GridItemSpan(maxLineSpan) }) { MangaRail("Son güncellenenler", publicCatalog(state.updated), onManga) }
        }
        if (visible.isEmpty() && !state.loading && state.error == null) item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogEmptyState(if (state.query.isBlank()) "Vitrinin henüz boş" else "Sonuç bulunamadı", if (state.query.isBlank()) "Bir kaynak ekleyip seri meta verilerini eşitlediğinde serilerin burada görünür." else "Başka bir seri adı veya tür dene.")
        }
        items(visible, key = { it.id }) { manga ->
            MangaCard(manga, columns, { onManga(manga) }, Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun MangaRail(title: String, manga: List<Manga>, onManga: (Manga) -> Unit) {
    if (manga.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(manga, key = { it.id }) { item -> MangaCard(item, 3, { onManga(item) }, Modifier.width(128.dp)) }
        }
    }
}
