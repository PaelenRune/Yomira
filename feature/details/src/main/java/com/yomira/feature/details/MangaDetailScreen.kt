package com.yomira.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.model.Chapter
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.RecommendedSeries
import com.yomira.core.model.RelatedSeries
import com.yomira.core.model.RelationRole
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.*
import com.yomira.domain.extension.SmartMatchResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class DownloadState { NOT_DOWNLOADED, QUEUED, DOWNLOADING, DOWNLOADED, FAILED }
data class ChapterUiState(val chapter: Chapter, val read: Boolean = false, val download: DownloadState = DownloadState.NOT_DOWNLOADED)

/**
 * Localized texts of the detail sheet and the smart source-match banner. The app module
 * builds it from string resources; the `%1$s`/`%1$d` placeholders stay in the resources.
 */
data class DetailCopy(
    val back: String = "Geri",
    val lockFast: String = "Hızlı kilitle",
    val statusOngoing: String = "Devam ediyor",
    val statusCompleted: String = "Tamamlandı",
    val statusHiatus: String = "Ara verildi",
    val statusCancelled: String = "İptal edildi",
    val statusUnknown: String = "Yayın durumu bilinmiyor",
    val saveAdd: String = "Kitaplığa ekle",
    val saveRemove: String = "Kitaplıktan çıkar",
    val authorLine: String = "Yazar",
    val artistLine: String = "Çizer",
    val notSpecified: String = "Belirtilmedi",
    val synopsisMissing: String = "Bu seri için özet henüz mevcut değil.",
    val summaryCollapse: String = "Daha az göster",
    val summaryExpand: String = "Özetin tamamı",
    val readNow: String = "Hemen oku",
    val chaptersHeading: String = "Bölümler (%1\$d)",
    val sortNewest: String = "En yeni",
    val sortOldest: String = "En eski",
    val chaptersEmptyTitle: String = "Henüz bölüm yok",
    val chaptersEmptyMessage: String = "Türkçe kaynak eşleştirmesini çalıştır veya seri kaynağını eşitle.",
    val chapterFallback: String = "Bölüm %1\$s",
    val dateMissing: String = "Tarih belirtilmedi",
    val markRead: String = "Okundu olarak işaretle",
    val markUnread: String = "Okunmadı olarak işaretle",
    val downloaded: String = "İndirildi",
    val retryDownload: String = "İndirmeyi yeniden dene",
    val downloadChapter: String = "Bölümü indir",
    val matchTitle: String = "Türkçe kaynak eşleştirmesi",
    val matchComparing: String = "Kaynaklar karşılaştırılıyor…",
    val matchMissingExtension: String = "Bu seriyi Türkçe okumak için en iyi kaynak: %1\$s (%2\$d bölüm). Bu eklentiyi kurmak ister misin?",
    val matchInstall: String = "Eklentiyi kur",
    val matchReady: String = "%1\$s hazır · %2\$d bölüm",
    val matchNone: String = "Türkçe kaynak bulunamadı. Eklenti kataloğunu güncelleyip yeniden dene.",
    val matchUnavailable: String = "Kaynak eşleştirmesi kullanılamıyor.",
    val matchIdle: String = "Yüklü ve kullanılabilir kaynaklar arasından Türkçe bölümü en fazla olanı bul.",
    val matchFindBest: String = "En iyi kaynağı bul",
    val matchRematch: String = "Yeniden eşleştir",
    val relatedHeading: String = "İlgili Seriler",
    val relatedSubtitle: String = "Aynı evrenin diğer serileri",
    val recommendedHeading: String = "Bu Seriye Benzerler",
    val recommendedSubtitle: String = "Bunu okuyanlar bunları da beğendi",
    val relationPrequel: String = "Ön bölüm",
    val relationSequel: String = "Devam serisi",
    val relationSpinOff: String = "Spin-off",
    val relationSideStory: String = "Yan hikaye",
    val relationAdaptation: String = "Uyarlama",
    val relationAlternative: String = "Alternatif sürüm",
    val relationSource: String = "Kaynak",
    val relationParent: String = "Ana seri",
    val relationCharacter: String = "Karakter",
    val relationOther: String = "İlişkili",
    val catalog: CatalogCopy = CatalogCopy(),
) {
    /** Localized franchise badge shown on a card of the "Related series" strip. */
    fun relationLabel(role: RelationRole): String = when (role) {
        RelationRole.PREQUEL -> relationPrequel
        RelationRole.SEQUEL -> relationSequel
        RelationRole.SPIN_OFF -> relationSpinOff
        RelationRole.SIDE_STORY -> relationSideStory
        RelationRole.ADAPTATION -> relationAdaptation
        RelationRole.ALTERNATIVE -> relationAlternative
        RelationRole.SOURCE -> relationSource
        RelationRole.PARENT -> relationParent
        RelationRole.CHARACTER -> relationCharacter
        RelationRole.OTHER -> relationOther
    }
}

/**
 * Series detail.
 *
 * On phones ([twoPane] = false) it stays a single scroll: hero, synopsis, then chapters.
 * On expanded windows ([twoPane] = true) the chapter list becomes the left pane and the
 * cover, synopsis and actions the right pane, so a tablet reader never loses the chapter
 * they are browsing while reading the summary.
 */
@Composable
fun MangaDetailScreen(
    manga: Manga,
    chapters: List<ChapterUiState>,
    match: SmartMatchResult?,
    matching: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMatch: () -> Unit,
    onInstall: (String) -> Unit,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onDownload: (Chapter) -> Unit,
    onPanic: (() -> Unit)? = null,
    onContinue: ((Chapter) -> Unit)? = null,
    related: List<RelatedSeries> = emptyList(),
    recommended: List<RecommendedSeries> = emptyList(),
    connectionsLoading: Boolean = false,
    onOpenSeries: (id: String, title: String) -> Unit = { _, _ -> },
    copy: DetailCopy = DetailCopy(),
    twoPane: Boolean = false,
) {
    if (twoPane) {
        TwoPaneDetail(
            manga = manga, chapters = chapters, match = match, matching = matching, saved = saved,
            onBack = onBack, onSave = onSave, onMatch = onMatch, onInstall = onInstall,
            onRead = onRead, onToggleRead = onToggleRead, onDownload = onDownload,
            onPanic = onPanic, onContinue = onContinue, related = related, recommended = recommended,
            connectionsLoading = connectionsLoading, onOpenSeries = onOpenSeries, copy = copy,
        )
    } else {
        SinglePaneDetail(
            manga = manga, chapters = chapters, match = match, matching = matching, saved = saved,
            onBack = onBack, onSave = onSave, onMatch = onMatch, onInstall = onInstall,
            onRead = onRead, onToggleRead = onToggleRead, onDownload = onDownload,
            onPanic = onPanic, onContinue = onContinue, related = related, recommended = recommended,
            connectionsLoading = connectionsLoading, onOpenSeries = onOpenSeries, copy = copy,
        )
    }
}

@Composable
private fun SinglePaneDetail(
    manga: Manga,
    chapters: List<ChapterUiState>,
    match: SmartMatchResult?,
    matching: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMatch: () -> Unit,
    onInstall: (String) -> Unit,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onDownload: (Chapter) -> Unit,
    onPanic: (() -> Unit)?,
    onContinue: ((Chapter) -> Unit)?,
    related: List<RelatedSeries>,
    recommended: List<RecommendedSeries>,
    connectionsLoading: Boolean,
    onOpenSeries: (String, String) -> Unit,
    copy: DetailCopy,
) {
    var expanded by rememberSaveable(manga.id) { mutableStateOf(false) }
    var descending by rememberSaveable(manga.id) { mutableStateOf(true) }
    val sorted = remember(chapters, descending) {
        if (descending) chapters.sortedByDescending { it.chapter.chapterNumber }
        else chapters.sortedBy { it.chapter.chapterNumber }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DetailHero(manga, onBack, onPanic, copy) }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SaveButton(saved, onSave, copy)
                MetadataBlock(manga, copy)
                Text(manga.synopsis ?: copy.synopsisMissing, maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                if (!manga.synopsis.isNullOrBlank()) TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) copy.summaryCollapse else copy.summaryExpand)
                }
                SmartMatchBanner(match, matching, onMatch, onInstall, copy)
                ReadNowButton(chapters, onContinue, onRead, copy)
                ChapterHeading(chapters.size, descending, { descending = !descending }, copy)
            }
        }
        if (chapters.isEmpty()) item { CatalogEmptyState(copy.chaptersEmptyTitle, copy.chaptersEmptyMessage) }
        items(sorted, key = { it.chapter.id }) { entry ->
            ChapterRow(entry, onRead, onToggleRead, onDownload, copy)
        }
        item {
            FranchiseShowcases(
                related = related, recommended = recommended, connectionsLoading = connectionsLoading,
                onOpenSeries = onOpenSeries, copy = copy,
            )
        }
    }
}

/**
 * Expanded-window layout: a persistent chapter list on the left, the cover and full
 * summary on the right. Both panes scroll independently, so browsing far down the
 * chapter list never hides the series summary.
 */
@Composable
private fun TwoPaneDetail(
    manga: Manga,
    chapters: List<ChapterUiState>,
    match: SmartMatchResult?,
    matching: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMatch: () -> Unit,
    onInstall: (String) -> Unit,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onDownload: (Chapter) -> Unit,
    onPanic: (() -> Unit)?,
    onContinue: ((Chapter) -> Unit)?,
    related: List<RelatedSeries>,
    recommended: List<RecommendedSeries>,
    connectionsLoading: Boolean,
    onOpenSeries: (String, String) -> Unit,
    copy: DetailCopy,
) {
    var descending by rememberSaveable(manga.id) { mutableStateOf(true) }
    val sorted = remember(chapters, descending) {
        if (descending) chapters.sortedByDescending { it.chapter.chapterNumber }
        else chapters.sortedBy { it.chapter.chapterNumber }
    }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(.42f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, copy.back) }
                Text(
                    String.format(copy.chaptersHeading, chapters.size),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { descending = !descending }) {
                    Icon(Icons.Default.Sort, null)
                    Text(if (descending) copy.sortNewest else copy.sortOldest)
                }
            }
            if (chapters.isEmpty()) {
                CatalogEmptyState(copy.chaptersEmptyTitle, copy.chaptersEmptyMessage)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(sorted, key = { it.chapter.id }) { entry ->
                        ChapterRow(entry, onRead, onToggleRead, onDownload, copy)
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = .35f)))
        LazyColumn(
            Modifier.weight(.58f).fillMaxHeight(),
            contentPadding = PaddingValues(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { TwoPaneHero(manga, copy) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SaveButton(saved, onSave, copy)
                    onPanic?.let { action ->
                        FilledTonalButton(onClick = action) {
                            Icon(Icons.Default.Lock, null)
                            Spacer(Modifier.width(8.dp))
                            Text(copy.lockFast)
                        }
                    }
                }
            }
            item { MetadataBlock(manga, copy) }
            item {
                Text(
                    manga.synopsis ?: copy.synopsisMissing,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item { SmartMatchBanner(match, matching, onMatch, onInstall, copy) }
            item { ReadNowButton(chapters, onContinue, onRead, copy) }
            item {
                FranchiseShowcases(
                    related = related, recommended = recommended, connectionsLoading = connectionsLoading,
                    onOpenSeries = onOpenSeries, copy = copy,
                )
            }
        }
    }
}

@Composable
private fun DetailHero(manga: Manga, onBack: () -> Unit, onPanic: (() -> Unit)?, copy: DetailCopy) {
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        MangaArtwork(manga.bannerUrl ?: manga.coverUrl, manga.title, Modifier.matchParentSize(), copy.catalog)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(DeepOledBlack.copy(alpha = .25f), DeepOledBlack))))
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, copy.back)
        }
        onPanic?.let { action ->
            FilledTonalButton(onClick = action, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Lock, null)
                Text(copy.lockFast)
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            MangaArtwork(manga.coverUrl, manga.title, Modifier.width(120.dp).aspectRatio(.68f).clip(RoundedCornerShape(16.dp)), copy.catalog)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(manga.title, style = MaterialTheme.typography.headlineMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                manga.score?.let { ScoreBadge(it, copy.catalog) }
                Text(statusLabel(manga, copy), style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
        }
    }
}

/** Right-pane hero: landscape banner under a sharp scrim, portrait cover as fallback. */
@Composable
private fun TwoPaneHero(manga: Manga, copy: DetailCopy) {
    val banner = manga.bannerUrl?.takeIf { it.isNotBlank() }
    Box(
        Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp)),
    ) {
        MangaArtwork(banner ?: manga.coverUrl, manga.title, Modifier.matchParentSize(), copy.catalog)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.3f to DeepOledBlack.copy(alpha = .1f),
                    1f to DeepOledBlack.copy(alpha = .94f),
                ),
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            MangaArtwork(manga.coverUrl, manga.title, Modifier.width(150.dp).aspectRatio(.68f).clip(RoundedCornerShape(16.dp)), copy.catalog)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                manga.score?.let { ScoreBadge(it, copy.catalog) }
                Text(manga.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(statusLabel(manga, copy), style = MaterialTheme.typography.labelLarge, color = TextMuted)
            }
        }
    }
}

@Composable
private fun SaveButton(saved: Boolean, onSave: () -> Unit, copy: DetailCopy) {
    FilledTonalButton(onClick = onSave) {
        Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
        Spacer(Modifier.width(8.dp))
        Text(if (saved) copy.saveRemove else copy.saveAdd)
    }
}

@Composable
private fun MetadataBlock(manga: Manga, copy: DetailCopy) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${copy.authorLine}: ${manga.author ?: copy.notSpecified}\n${copy.artistLine}: ${manga.artist ?: copy.notSpecified}",
            color = TextMuted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            manga.genres.forEach { genre ->
                Surface(color = CardSurface, shape = RoundedCornerShape(10.dp)) {
                    Text(genre, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ReadNowButton(
    chapters: List<ChapterUiState>,
    onContinue: ((Chapter) -> Unit)?,
    onRead: (Chapter) -> Unit,
    copy: DetailCopy,
) {
    val nextChapter = chapters.filterNot { it.read }.minByOrNull { it.chapter.chapterNumber }
        ?: chapters.minByOrNull { it.chapter.chapterNumber }
    Button(onClick = { nextChapter?.let { (onContinue ?: onRead)(it.chapter) } }, enabled = nextChapter != null, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.MenuBook, null)
        Spacer(Modifier.width(8.dp))
        Text(copy.readNow)
    }
}

@Composable
private fun ChapterHeading(count: Int, descending: Boolean, onToggle: () -> Unit, copy: DetailCopy) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(String.format(copy.chaptersHeading, count), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onToggle) {
            Icon(Icons.Default.Sort, null)
            Text(if (descending) copy.sortNewest else copy.sortOldest)
        }
    }
}

@Composable
private fun ChapterRow(
    entry: ChapterUiState,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onDownload: (Chapter) -> Unit,
    copy: DetailCopy,
) {
    ListItem(
        modifier = Modifier.clickable { onRead(entry.chapter) },
        headlineContent = { Text(entry.chapter.title ?: String.format(copy.chapterFallback, entry.chapter.chapterNumber)) },
        supportingContent = {
            Text(
                entry.chapter.releaseDate?.atZone(ZoneId.systemDefault())
                    ?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: copy.dateMissing,
            )
        },
        leadingContent = {
            IconButton(onClick = { onToggleRead(entry.chapter) }) {
                Icon(
                    if (entry.read) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    if (entry.read) copy.markUnread else copy.markRead,
                    tint = if (entry.read) NeonPurple else TextMuted,
                )
            }
        },
        trailingContent = {
            IconButton(
                onClick = { onDownload(entry.chapter) },
                enabled = entry.download !in setOf(DownloadState.QUEUED, DownloadState.DOWNLOADING),
            ) {
                when (entry.download) {
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    DownloadState.DOWNLOADED -> Icon(Icons.Default.DownloadDone, copy.downloaded)
                    DownloadState.FAILED -> Icon(Icons.Default.Refresh, copy.retryDownload)
                    DownloadState.NOT_DOWNLOADED -> Icon(Icons.Default.Download, copy.downloadChapter)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = DeepOledBlack),
    )
}

@Composable
private fun FranchiseShowcases(
    related: List<RelatedSeries>,
    recommended: List<RecommendedSeries>,
    connectionsLoading: Boolean,
    onOpenSeries: (String, String) -> Unit,
    copy: DetailCopy,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (connectionsLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonPurple)
        }
        if (related.isNotEmpty()) {
            SeriesShowcaseRail(
                title = copy.relatedHeading,
                subtitle = copy.relatedSubtitle,
                entries = related.map { entry ->
                    SeriesShowcaseEntry(
                        id = entry.id,
                        title = entry.title,
                        coverUrl = entry.coverUrl,
                        badge = copy.relationLabel(entry.role),
                        score = null,
                    )
                },
                onOpenSeries = onOpenSeries,
                copy = copy,
            )
        }
        if (recommended.isNotEmpty()) {
            SeriesShowcaseRail(
                title = copy.recommendedHeading,
                subtitle = copy.recommendedSubtitle,
                entries = recommended.map { entry ->
                    SeriesShowcaseEntry(
                        id = entry.id,
                        title = entry.title,
                        coverUrl = entry.coverUrl,
                        badge = null,
                        score = entry.score,
                    )
                },
                onOpenSeries = onOpenSeries,
                copy = copy,
            )
        }
    }
}

private fun statusLabel(manga: Manga, copy: DetailCopy): String = when (manga.status) {
    MangaStatus.ONGOING -> copy.statusOngoing
    MangaStatus.COMPLETED -> copy.statusCompleted
    MangaStatus.HIATUS -> copy.statusHiatus
    MangaStatus.CANCELLED -> copy.statusCancelled
    MangaStatus.UNKNOWN -> copy.statusUnknown
}

/** One card of the connected-series showcases; [badge] carries the franchise role. */
private data class SeriesShowcaseEntry(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val badge: String?,
    val score: Double?,
)

/**
 * A titled horizontal showcase strip ("İlgili Seriler" / "Bu Seriye Benzerler").
 * Tapping a card opens that series' detail page through [onOpenSeries].
 */
@Composable
private fun SeriesShowcaseRail(
    title: String,
    subtitle: String,
    entries: List<SeriesShowcaseEntry>,
    onOpenSeries: (id: String, title: String) -> Unit,
    copy: DetailCopy,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpenSeries(entry.id, entry.title) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        MangaArtwork(entry.coverUrl, entry.title, Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(14.dp)), copy.catalog)
                        entry.score?.let { score -> Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { ScoreBadge(score, copy.catalog) } }
                    }
                    Text(entry.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    entry.badge?.let { badge ->
                        Surface(color = NeonPurple.copy(alpha = .16f), shape = RoundedCornerShape(8.dp)) {
                            Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = NeonPurple, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartMatchBanner(result: SmartMatchResult?, loading: Boolean, onMatch: () -> Unit, onInstall: (String) -> Unit, copy: DetailCopy = DetailCopy()) {
    Surface(shape = RoundedCornerShape(20.dp), color = NeonPurple.copy(alpha = .12f)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(copy.matchTitle, style = MaterialTheme.typography.titleMedium)
            when {
                loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(copy.matchComparing) }
                result is SmartMatchResult.MissingExtension -> {
                    Text(String.format(copy.matchMissingExtension, result.suggestedSource.source.name, result.chapterCount))
                    Button(onClick = { onInstall(result.installUrl) }) { Text(copy.matchInstall) }
                }
                result is SmartMatchResult.Ready -> Text(String.format(copy.matchReady, result.selected.source.name, result.chapters.size))
                result is SmartMatchResult.NoMatch -> Text(copy.matchNone)
                result is SmartMatchResult.Unavailable -> Text("${copy.matchUnavailable} ${result.failures.joinToString { it.reason }}")
                else -> Text(copy.matchIdle)
            }
            if (!loading) TextButton(onClick = onMatch) { Text(if (result == null) copy.matchFindBest else copy.matchRematch) }
        }
    }
}
