package com.yomira.feature.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val readStart: String = "Okumaya başla",
    val readContinue: String = "Kaldığın yerden devam et",
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
    val nextChapter = chapters.filterNot { it.read }.minByOrNull { it.chapter.chapterNumber }
        ?: chapters.minByOrNull { it.chapter.chapterNumber }
    val started = chapters.any { it.read }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = if (nextChapter != null) 116.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DetailHero(manga, onBack, onPanic, copy) }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SaveButton(saved, onSave, copy)
                        onPanic?.let { action ->
                            FilledTonalButton(onClick = action, shape = RoundedCornerShape(percent = 50)) {
                                Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(copy.lockFast, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    MetadataBlock(manga, copy)
                    SynopsisBlock(manga, expanded, onToggle = { expanded = !expanded }, copy)
                    SmartMatchBanner(match, matching, onMatch, onInstall, copy)
                    ChapterHeading(chapters.size, descending, { descending = !descending }, copy)
                }
            }
            if (chapters.isEmpty()) item { CatalogEmptyState(copy.chaptersEmptyTitle, copy.chaptersEmptyMessage) }
            items(sorted, key = { it.chapter.id }) { entry ->
                ChapterRow(entry, onRead, onToggleRead, onDownload, copy, Modifier.padding(horizontal = 20.dp))
            }
            item {
                FranchiseShowcases(
                    related = related, recommended = recommended, connectionsLoading = connectionsLoading,
                    onOpenSeries = onOpenSeries, copy = copy,
                )
            }
        }
        if (nextChapter != null) {
            FloatingReadAction(
                next = nextChapter.chapter,
                started = started,
                copy = copy,
                onClick = { (onContinue ?: onRead)(nextChapter.chapter) },
                modifier = Modifier.align(Alignment.BottomCenter),
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
                        ChapterRow(entry, onRead, onToggleRead, onDownload, copy, Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
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
    Box(Modifier.fillMaxWidth().height(400.dp).clipToBounds()) {
        // The cover dissolves into the page: blurred backdrop, a bottom-anchored
        // portrait cover and a scrim that lands exactly on the app canvas.
        MangaArtwork(
            manga.bannerUrl ?: manga.coverUrl,
            manga.title,
            Modifier.matchParentSize().blur(22.dp),
            copy.catalog,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    // Keep the blurred art visible above the title block: the
                    // scrim only has to carry the cover and the type.
                    0f to DeepOledBlack.copy(alpha = .10f),
                    .34f to DeepOledBlack.copy(alpha = .34f),
                    .74f to DeepOledBlack.copy(alpha = .86f),
                    1f to DeepOledBlack,
                ),
            ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = DeepOledBlack.copy(alpha = .52f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
                tonalElevation = 0.dp,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    copy.back,
                    modifier = Modifier.padding(9.dp).size(20.dp),
                    tint = TextPrimary,
                )
            }
        }
        onPanic?.let { action ->
            FilledTonalButton(
                onClick = action,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(copy.lockFast, style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            MangaArtwork(
                manga.coverUrl,
                manga.title,
                Modifier
                    .width(116.dp)
                    .aspectRatio(.68f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(16.dp)),
                copy.catalog,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    manga.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                // The author line lives in the metadata block below; repeating it
                // here only pushed the score/status row down on a phone.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    manga.score?.let { ScoreBadge(it, copy.catalog) }
                    YomiraBadge(text = statusLabel(manga, copy))
                }
                manga.genres.take(2).takeIf { it.isNotEmpty() }?.let { genres ->
                    Text(
                        genres.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The synopsis opens in place with a calm gradient fade instead of a hard clamp. */
@Composable
private fun SynopsisBlock(manga: Manga, expanded: Boolean, onToggle: () -> Unit, copy: DetailCopy) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            manga.synopsis ?: copy.synopsisMissing,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (!manga.synopsis.isNullOrBlank()) {
            TextButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
            ) {
                Text(
                    if (expanded) copy.summaryCollapse else copy.summaryExpand,
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonLavender,
                )
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
    FilledTonalButton(
        onClick = onSave,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = NeonPurple.copy(alpha = .16f),
            contentColor = if (saved) NeonLavender else TextPrimary,
        ),
    ) {
        Icon(
            if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            null,
            Modifier.size(18.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(if (saved) copy.saveRemove else copy.saveAdd, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Metadata as calm capsule chips: author/artist line first (the only long text),
 * then score, status, year and the genre matrix. Nothing here uses a boxed card,
 * so the page keeps one continuous reading rhythm.
 */
@Composable
private fun MetadataBlock(manga: Manga, copy: DetailCopy) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(15.dp), tint = TextMuted)
            Text(
                buildString {
                    append(copy.authorLine)
                    append(" · ")
                    append(manga.author ?: copy.notSpecified)
                    if (!manga.artist.isNullOrBlank() && manga.artist != manga.author) {
                        append("   ")
                        append(copy.artistLine)
                        append(" · ")
                        append(manga.artist)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            manga.score?.let { score ->
                YomiraBadge(
                    text = "%.1f".format(score),
                    icon = Icons.Default.Star,
                    accent = RatingGold,
                    contentColor = RatingGold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            YomiraBadge(text = statusLabel(manga, copy), accent = NeonIndigo)
            manga.genres.forEach { genre ->
                YomiraBadge(text = genre)
            }
        }
    }
}

/** Primary chapter action. Label follows the reader's actual position in the series. */
@Composable
private fun ReadNowButton(
    chapters: List<ChapterUiState>,
    onContinue: ((Chapter) -> Unit)?,
    onRead: (Chapter) -> Unit,
    copy: DetailCopy,
) {
    val nextChapter = chapters.filterNot { it.read }.minByOrNull { it.chapter.chapterNumber }
        ?: chapters.minByOrNull { it.chapter.chapterNumber }
    val started = chapters.any { it.read }
    Button(
        onClick = { nextChapter?.let { (onContinue ?: onRead)(it.chapter) } },
        enabled = nextChapter != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (started) copy.readContinue else copy.readStart, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChapterHeading(count: Int, descending: Boolean, onToggle: () -> Unit, copy: DetailCopy) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(String.format(copy.chaptersHeading, count), style = MaterialTheme.typography.titleLarge)
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(percent = 50),
            color = SurfaceRaised.copy(alpha = .7f),
            border = BorderStroke(1.dp, HairlineBorder),
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Default.Sort, null, Modifier.size(15.dp), tint = NeonLavender)
                Text(
                    if (descending) copy.sortNewest else copy.sortOldest,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
            }
        }
    }
}

/**
 * One chapter row: a soft bordered capsule card, a numbered/checked leading tile
 * that also toggles the read flag, then the download affordance. Read chapters
 * drop to a muted title and keep a lavender check, so progress is obvious at a
 * glance without a second accent colour.
 */
@Composable
private fun ChapterRow(
    entry: ChapterUiState,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onDownload: (Chapter) -> Unit,
    copy: DetailCopy,
    modifier: Modifier = Modifier,
) {
    YomiraCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = if (entry.read) CardSurface.copy(alpha = .55f) else CardSurface,
        onClick = { onRead(entry.chapter) },
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = { onToggleRead(entry.chapter) },
                shape = CircleShape,
                color = if (entry.read) NeonPurple.copy(alpha = .18f) else SurfaceRaised,
                border = BorderStroke(
                    1.dp,
                    if (entry.read) NeonPurple.copy(alpha = .4f) else HairlineBorder,
                ),
                tonalElevation = 0.dp,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (entry.read) {
                        Icon(Icons.Default.Check, copy.markUnread, Modifier.size(17.dp), tint = NeonLavender)
                    } else {
                        Text(
                            chapterNumberLabel(entry.chapter.chapterNumber),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.chapter.title ?: String.format(copy.chapterFallback, entry.chapter.chapterNumber),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (entry.read) TextMuted else TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.chapter.releaseDate?.atZone(ZoneId.systemDefault())
                            ?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: copy.dateMissing,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                    if (entry.download == DownloadState.DOWNLOADED) {
                        Icon(Icons.Default.DownloadDone, copy.downloaded, Modifier.size(13.dp), tint = NeonLavender)
                    }
                }
            }
            when (entry.download) {
                DownloadState.DOWNLOADING, DownloadState.QUEUED -> {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonLavender)
                }
                DownloadState.DOWNLOADED -> Icon(
                    Icons.Default.DownloadDone,
                    copy.downloaded,
                    Modifier.padding(10.dp).size(18.dp),
                    tint = NeonLavender,
                )
                else -> IconButton(
                    onClick = { onDownload(entry.chapter) },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        if (entry.download == DownloadState.FAILED) Icons.Default.Refresh else Icons.Default.Download,
                        if (entry.download == DownloadState.FAILED) copy.retryDownload else copy.downloadChapter,
                        Modifier.size(19.dp),
                        tint = if (entry.download == DownloadState.FAILED) MaterialTheme.colorScheme.error else TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * The floating resume capsule pinned above the navigation bar. It reads as the
 * only primary action on the page: a violet → lavender gradient inside a violet
 * hairline, with the next chapter's title as supporting text.
 */
@Composable
private fun FloatingReadAction(
    next: Chapter,
    started: Boolean,
    copy: DetailCopy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(percent = 50),
            color = Color.Transparent,
            border = BorderStroke(1.dp, NeonLavender.copy(alpha = .3f)),
            shadowElevation = 18.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .background(Brush.horizontalGradient(listOf(NeonPurple, NeonViolet)))
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    null,
                    Modifier.size(21.dp),
                    tint = TextPrimary,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        if (started) copy.readContinue else copy.readStart,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                    )
                    Text(
                        next.title ?: String.format(copy.chapterFallback, next.chapterNumber),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary.copy(alpha = .74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    Modifier.size(18.dp),
                    tint = TextPrimary.copy(alpha = .85f),
                )
            }
        }
    }
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

/** "12" for whole chapters, "12.5" for half chapters. */
private fun chapterNumberLabel(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

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
    YomiraCard(
        shape = RoundedCornerShape(20.dp),
        containerColor = NeonPurple.copy(alpha = .10f),
        glow = result != null,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Translate, null, Modifier.size(16.dp), tint = NeonLavender)
                Text(copy.matchTitle, style = MaterialTheme.typography.titleMedium)
            }
            when {
                loading -> { LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = NeonLavender); Text(copy.matchComparing, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
                result is SmartMatchResult.MissingExtension -> {
                    Text(String.format(copy.matchMissingExtension, result.suggestedSource.source.name, result.chapterCount), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Button(onClick = { onInstall(result.installUrl) }, shape = RoundedCornerShape(percent = 50)) { Text(copy.matchInstall) }
                }
                result is SmartMatchResult.Ready -> Text(String.format(copy.matchReady, result.selected.source.name, result.chapters.size), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                result is SmartMatchResult.NoMatch -> Text(copy.matchNone, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                result is SmartMatchResult.Unavailable -> Text("${copy.matchUnavailable} ${result.failures.joinToString { it.reason }}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                else -> Text(copy.matchIdle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            if (!loading) {
                Surface(
                    onClick = onMatch,
                    shape = RoundedCornerShape(percent = 50),
                    color = NeonPurple.copy(alpha = .16f),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = .28f)),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        if (result == null) copy.matchFindBest else copy.matchRematch,
                        Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonLavender,
                    )
                }
            }
        }
    }
}
