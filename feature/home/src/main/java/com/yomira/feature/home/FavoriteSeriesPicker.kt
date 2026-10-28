package com.yomira.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yomira.core.model.Manga
import com.yomira.core.ui.MangaArtwork
import com.yomira.domain.extension.FavoriteSeries

internal val FavoriteSeriesSaver = listSaver<List<FavoriteSeries>, String>(
    save = { it.flatMap { item -> listOf(item.catalogId.orEmpty(), item.title) } },
    restore = { it.chunked(2).map { pair -> FavoriteSeries(pair[0].ifEmpty { null }, pair[1]) } },
)

/** Same local Room snapshot and title/alias/genre matching as Home; never starts a network search. */
internal fun searchLocalCatalog(catalog: List<Manga>, query: String): List<Manga> = catalog.filter {
    it.title.contains(query.trim(), true) || it.altTitles.any { title -> title.contains(query.trim(), true) } ||
        it.genres.any { genre -> genre.contains(query.trim(), true) }
}

internal fun toggleFavoriteSeries(selected: List<FavoriteSeries>, item: FavoriteSeries): List<FavoriteSeries> = when {
    item in selected -> selected - item
    selected.size < 5 -> selected + item
    else -> selected
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavoriteSeriesPicker(
    catalog: List<Manga>, selected: List<FavoriteSeries>, enabled: Boolean,
    copy: OnboardingCopy, onSelection: (List<FavoriteSeries>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(catalog, query) { searchLocalCatalog(catalog, query).distinctBy { it.id }.take(20) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${selected.size} / 5", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.forEach { item ->
                InputChip(selected = true, enabled = enabled, onClick = { onSelection(selected - item) },
                    label = { Text("${copy.removeFavorite}: ${item.title} ×") })
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, enabled = enabled,
            label = { Text(copy.seriesSearch) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (results.isEmpty()) Text(copy.seriesEmpty, style = MaterialTheme.typography.bodyMedium)
        results.forEach { manga ->
            val item = FavoriteSeries(manga.id, manga.title)
            val checked = item in selected
            Surface(onClick = { onSelection(toggleFavoriteSeries(selected, item)) },
                enabled = enabled && (checked || selected.size < 5),
                color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().testTag("favorite-series-${manga.id}")) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MangaArtwork(manga.coverUrl, manga.title, Modifier.width(40.dp).height(60.dp))
                    Text(manga.title, Modifier.weight(1f), maxLines = 2)
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
