package com.yomira.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.model.Chapter
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.*
import com.yomira.domain.extension.SmartMatchResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DownloadState { NOT_DOWNLOADED, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }
data class ChapterUiState(val chapter: Chapter, val read: Boolean = false, val download: DownloadState = DownloadState.NOT_DOWNLOADED)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaDetailScreen(manga: Manga, chapters: List<ChapterUiState>, match: SmartMatchResult?, matching: Boolean, saved: Boolean, onBack: () -> Unit, onSave: () -> Unit, onMatch: () -> Unit, onInstall: (String) -> Unit, onRead: (Chapter) -> Unit, onToggleRead: (Chapter) -> Unit, onDownload: (Chapter) -> Unit, onPanic: (() -> Unit)? = null, onContinue: ((Chapter) -> Unit)? = null) {
    var expanded by rememberSaveable(manga.id) { mutableStateOf(false) }
    var descending by rememberSaveable(manga.id) { mutableStateOf(true) }
    val sorted = remember(chapters, descending) { if (descending) chapters.sortedByDescending { it.chapter.chapterNumber } else chapters.sortedBy { it.chapter.chapterNumber } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                MangaArtwork(manga.bannerUrl ?: manga.coverUrl, manga.title, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(DeepOledBlack.copy(alpha = .25f), DeepOledBlack))))
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                onPanic?.let { action -> FilledTonalButton(onClick = action, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Default.Lock, null); Text("Hızlı kilitle") } }
                Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                    MangaArtwork(manga.coverUrl, manga.title, Modifier.width(120.dp).aspectRatio(.68f).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(manga.title, style = MaterialTheme.typography.headlineMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        manga.score?.let { ScoreBadge(it) }
                        Text(when (manga.status) {
                            MangaStatus.ONGOING -> "Devam ediyor"
                            MangaStatus.COMPLETED -> "Tamamlandı"
                            MangaStatus.HIATUS -> "Ara verildi"
                            MangaStatus.CANCELLED -> "İptal edildi"
                            MangaStatus.UNKNOWN -> "Yayın durumu bilinmiyor"
                        }, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onSave) { Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null); Text(if (saved) "Kitaplıktan çıkar" else "Kitaplığa ekle") }
                Text("Yazar: ${manga.author ?: "Belirtilmedi"}\nÇizer: ${manga.artist ?: "Belirtilmedi"}", color = TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    manga.genres.forEach { genre -> Surface(color = CardSurface, shape = RoundedCornerShape(10.dp)) {
                        Text(genre, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                    } }
                }
                Text(manga.synopsis ?: "Bu seri için özet henüz mevcut değil.", maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                if (!manga.synopsis.isNullOrBlank()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Daha az göster" else "Özetin tamamı") }
                SmartMatchBanner(match, matching, onMatch, onInstall)
                val nextChapter = chapters.filterNot { it.read }.minByOrNull { it.chapter.chapterNumber }
                    ?: chapters.minByOrNull { it.chapter.chapterNumber }
                Button(onClick = { nextChapter?.let { (onContinue ?: onRead)(it.chapter) } }, enabled = nextChapter != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MenuBook, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hemen oku")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bölümler (${chapters.size})", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { descending = !descending }) { Icon(Icons.Default.Sort, null); Text(if (descending) "En yeni" else "En eski") }
                }
            }
        }
        if (chapters.isEmpty()) item { CatalogEmptyState("Henüz bölüm yok", "Türkçe kaynak eşleştirmesini çalıştır veya seri kaynağını eşitle.") }
        items(sorted, key = { it.chapter.id }) { entry ->
            ListItem(
                modifier = Modifier.clickable { onRead(entry.chapter) },
                headlineContent = { Text(entry.chapter.title ?: "Bölüm ${entry.chapter.chapterNumber}") },
                supportingContent = { Text(entry.chapter.releaseDate?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: "Tarih belirtilmedi") },
                leadingContent = { IconButton(onClick = { onToggleRead(entry.chapter) }) { Icon(if (entry.read) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (entry.read) "Okunmadı olarak işaretle" else "Okundu olarak işaretle", tint = if (entry.read) NeonPurple else TextMuted) } },
                trailingContent = { IconButton(onClick = { onDownload(entry.chapter) }, enabled = entry.download !in setOf(DownloadState.QUEUED, DownloadState.DOWNLOADING)) {
                    when (entry.download) {
                        DownloadState.DOWNLOADING, DownloadState.QUEUED -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        DownloadState.DOWNLOADED -> Icon(Icons.Default.DownloadDone, "İndirildi")
                        DownloadState.FAILED -> Icon(Icons.Default.Refresh, "İndirmeyi yeniden dene")
                        DownloadState.NOT_DOWNLOADED -> Icon(Icons.Default.Download, "Bölümü indir")
                    }
                } },
                colors = ListItemDefaults.colors(containerColor = DeepOledBlack),
            )
        }
    }
}

@Composable
fun SmartMatchBanner(result: SmartMatchResult?, loading: Boolean, onMatch: () -> Unit, onInstall: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = NeonPurple.copy(alpha = .12f)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Türkçe kaynak eşleştirmesi", style = MaterialTheme.typography.titleMedium)
            when {
                loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Kaynaklar karşılaştırılıyor…") }
                result is SmartMatchResult.MissingExtension -> {
                    Text("Bu seriyi Türkçe okumak için en iyi kaynak: ${result.suggestedSource.source.name} (${result.chapterCount} bölüm). Bu eklentiyi kurmak ister misin?")
                    Button(onClick = { onInstall(result.installUrl) }) { Text("Eklentiyi kur") }
                }
                result is SmartMatchResult.Ready -> Text("${result.selected.source.name} hazır · ${result.chapters.size} bölüm")
                result is SmartMatchResult.NoMatch -> Text("Türkçe kaynak bulunamadı. Eklenti kataloğunu güncelleyip yeniden dene.")
                result is SmartMatchResult.Unavailable -> Text("Kaynak eşleştirmesi kullanılamıyor. ${result.failures.joinToString { it.reason }}")
                else -> Text("Yüklü ve kullanılabilir kaynaklar arasından Türkçe bölümü en fazla olanı bul.")
            }
            if (!loading) TextButton(onClick = onMatch) { Text(if (result == null) "En iyi kaynağı bul" else "Yeniden eşleştir") }
        }
    }
}
