package com.yomira.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.model.Chapter
import com.yomira.core.model.Manga
import com.yomira.core.ui.CatalogEmptyState
import java.time.Instant

data class LibraryUpdateUi(val manga: Manga, val chapter: Chapter)

/** A higher read chapter is the watermark; chapter numbers need not be integral. */
fun unreadLibraryChapters(chapters: List<Chapter>, readIds: Set<String>, lastReadNumber: Double?): List<Chapter> {
    val watermark = listOfNotNull(lastReadNumber, chapters.filter { it.id in readIds }.maxOfOrNull { it.chapterNumber }).maxOrNull()
    return chapters.distinctBy { it.id }.filter { it.id !in readIds && (watermark == null || it.chapterNumber > watermark) }
}

fun chronologicalLibraryUpdates(updates: List<LibraryUpdateUi>): List<LibraryUpdateUi> = updates
    .filter { it.manga.contentRating != com.yomira.core.model.ContentRating.EXPLICIT }
    .distinctBy { it.chapter.id }
    .sortedWith(compareByDescending<LibraryUpdateUi> { it.chapter.releaseDate ?: Instant.EPOCH }
        .thenBy { it.manga.id }.thenByDescending { it.chapter.chapterNumber }.thenBy { it.chapter.id })

data class LibraryCopy(
    val series: String = "Seriler",
    val updates: String = "Güncellemeler",
    val read: String = "▶ Oku",
    val chapter: String = "Bölüm %1\$s",
    val emptyTitle: String = "Yeni bölüm yok",
    val emptyMessage: String = "Kitaplığına eklediğin serilerin okunmamış bölümleri burada görünür.",
    val refresh: String = "Güncellemeleri kontrol et",
)

@Composable
fun LibraryScreen(updates: List<LibraryUpdateUi>, onRead: (LibraryUpdateUi) -> Unit, copy: LibraryCopy = LibraryCopy(), onRefresh: () -> Unit = {}, refreshing: Boolean = false, refreshError: String? = null, series: @Composable () -> Unit) {
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (showUpdates) 1 else 0) {
            Tab(selected = !showUpdates, onClick = { showUpdates = false }, text = { Text(copy.series) })
            Tab(selected = showUpdates, onClick = { showUpdates = true }, text = { Text("${copy.updates} (${updates.size})") })
        }
        if (showUpdates) {
            TextButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.align(Alignment.End)) { Text(copy.refresh) }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            refreshError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        }
        Box(Modifier.weight(1f)) {
            if (!showUpdates) series()
            else if (updates.isEmpty()) CatalogEmptyState(copy.emptyTitle, copy.emptyMessage)
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(updates, key = { it.chapter.id }) { update ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(update.manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val number = update.chapter.chapterNumber.toBigDecimal().stripTrailingZeros().toPlainString()
                                Text(String.format(copy.chapter, number), style = MaterialTheme.typography.bodyMedium)
                                update.chapter.releaseDate?.let { Text(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(), style = MaterialTheme.typography.labelSmall) }
                            }
                            TextButton(onClick = { onRead(update) }) { Text(copy.read) }
                        }
                    }
                }
            }
        }
    }
}
