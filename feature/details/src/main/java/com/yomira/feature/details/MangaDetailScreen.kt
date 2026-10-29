package com.yomira.feature.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.model.Chapter
import com.yomira.core.model.AdaptationKind
import com.yomira.core.model.WatchableAdaptation
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.RecommendedSeries
import com.yomira.core.model.RelatedSeries
import com.yomira.core.model.RelationRole
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class ChapterUiState(val chapter: Chapter, val read: Boolean = false)

internal fun visibleChapters(
    chapters: List<ChapterUiState>,
    descending: Boolean,
    unreadOnly: Boolean,
): List<ChapterUiState> {
    val visible = if (unreadOnly) chapters.filterNot { it.read } else chapters
    return if (descending) visible.sortedByDescending { it.chapter.chapterNumber }
    else visible.sortedBy { it.chapter.chapterNumber }
}

/**
 * Localized texts of the detail sheet. The app module
 * builds it from string resources; the `%1$s`/`%1$d` placeholders stay in the resources.
 */
@Immutable
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
    val chaptersEmptyMessage: String = "Seçtiğin dilde okunabilir bölüm bulunamadı.",
    val chaptersLoading: String = "En iyi kaynak taranıyor ve bölümler yükleniyor…",
    val chapterFallback: String = "Bölüm %1\$s",
    val dateMissing: String = "Tarih belirtilmedi",
    val markRead: String = "Okundu olarak işaretle",
    val markUnread: String = "Okunmadı olarak işaretle",
    val markReadThrough: String = "Buraya kadar olan bölümleri okundu say",
    val readLabel: String = "Okundu",
    val unreadOnly: String = "Sadece Okunmamışlar",
    val unreadEmptyTitle: String = "Okunmamış bölüm yok",
    val unreadEmptyMessage: String = "Tüm bölümleri görmek için filtreyi kapat.",
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
    val adaptationAnime: String = "Anime Uyarlaması",
    val adaptationHentaiOva: String = "Hentai OVA",
    val adaptationLiveAction: String = "Dizi / Canlı Uyarlama",
    val adaptationDonghua: String = "Donghua Uyarlaması",
    val adaptationSpecial: String = "Özel Bölüm",
    val adaptationEpisodes: String = "%1\$d Bölüm",
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

    fun adaptationLabel(kind: AdaptationKind): String = when (kind) {
        AdaptationKind.ANIME -> adaptationAnime
        AdaptationKind.HENTAI_OVA -> adaptationHentaiOva
        AdaptationKind.LIVE_ACTION -> adaptationLiveAction
        AdaptationKind.DONGHUA -> adaptationDonghua
        AdaptationKind.SPECIAL -> adaptationSpecial
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
    matching: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onPanic: (() -> Unit)? = null,
    onContinue: ((Chapter) -> Unit)? = null,
    related: List<RelatedSeries> = emptyList(),
    recommended: List<RecommendedSeries> = emptyList(),
    connectionsLoading: Boolean = false,
    onOpenSeries: (id: String, title: String) -> Unit = { _, _ -> },
    copy: DetailCopy = DetailCopy(),
    twoPane: Boolean = false,
    adaptations: List<WatchableAdaptation> = emptyList(),
    onMarkReadThrough: (Chapter) -> Unit = {},
) {
    if (twoPane) {
        TwoPaneDetail(
            manga = manga, chapters = chapters, matching = matching, saved = saved,
            onBack = onBack, onSave = onSave,
            onRead = onRead, onToggleRead = onToggleRead,
            onPanic = onPanic, onContinue = onContinue, related = related, recommended = recommended,
            connectionsLoading = connectionsLoading, onOpenSeries = onOpenSeries, copy = copy,
            adaptations = adaptations,
            onMarkReadThrough = onMarkReadThrough,
        )
    } else {
        SinglePaneDetail(
            manga = manga, chapters = chapters, matching = matching, saved = saved,
            onBack = onBack, onSave = onSave,
            onRead = onRead, onToggleRead = onToggleRead,
            onPanic = onPanic, onContinue = onContinue, related = related, recommended = recommended,
            connectionsLoading = connectionsLoading, onOpenSeries = onOpenSeries, copy = copy,
            adaptations = adaptations,
            onMarkReadThrough = onMarkReadThrough,
        )
    }
}

@Composable
private fun SinglePaneDetail(
    manga: Manga,
    chapters: List<ChapterUiState>,
    matching: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onPanic: (() -> Unit)?,
    onContinue: ((Chapter) -> Unit)?,
    related: List<RelatedSeries>,
    recommended: List<RecommendedSeries>,
    adaptations: List<WatchableAdaptation>,
    connectionsLoading: Boolean,
    onOpenSeries: (String, String) -> Unit,
    copy: DetailCopy,
    onMarkReadThrough: (Chapter) -> Unit,
) {
    var expanded by rememberSaveable(manga.id) { mutableStateOf(false) }
    var descending by rememberSaveable(manga.id) { mutableStateOf(true) }
    var unreadOnly by rememberSaveable(manga.id) { mutableStateOf(false) }
    val sorted = remember(chapters, descending, unreadOnly) {
        visibleChapters(chapters, descending, unreadOnly)
    }
    val nextChapter = chapters.filterNot { it.read }.minByOrNull { it.chapter.chapterNumber }
        ?: chapters.minByOrNull { it.chapter.chapterNumber }
    val started = chapters.any { it.read }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = if (nextChapter != null) 116.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero", contentType = "hero") { DetailHero(manga, onBack, onPanic, copy) }
            item(key = "metadata", contentType = "metadata") {
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
                    MetadataBlock(manga, copy, adaptations)
                    if (adaptations.isNotEmpty()) AdaptationBadges(adaptations, copy)
                    SynopsisBlock(manga, expanded, onToggle = { expanded = !expanded }, copy)
                    ChapterHeading(sorted.size, descending, { descending = !descending }, copy,
                        unreadOnly, { unreadOnly = !unreadOnly })
                }
            }
            if (chapters.isEmpty()) item(key = "empty", contentType = "empty") {
                if (matching) ChaptersLoading(copy) else CatalogEmptyState(copy.chaptersEmptyTitle, copy.chaptersEmptyMessage)
            } else if (sorted.isEmpty()) item(key = "unread-empty", contentType = "empty") {
                CatalogEmptyState(copy.unreadEmptyTitle, copy.unreadEmptyMessage)
            }
            items(sorted, key = { it.chapter.id }, contentType = { "chapter" }) { entry ->
                ChapterRow(entry, onRead, onToggleRead, copy, Modifier.padding(horizontal = 20.dp), onMarkReadThrough)
            }
            item(key = "connections", contentType = "connections") {
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
    matching: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    onPanic: (() -> Unit)?,
    onContinue: ((Chapter) -> Unit)?,
    related: List<RelatedSeries>,
    recommended: List<RecommendedSeries>,
    adaptations: List<WatchableAdaptation>,
    connectionsLoading: Boolean,
    onOpenSeries: (String, String) -> Unit,
    copy: DetailCopy,
    onMarkReadThrough: (Chapter) -> Unit,
) {
    var descending by rememberSaveable(manga.id) { mutableStateOf(true) }
    var unreadOnly by rememberSaveable(manga.id) { mutableStateOf(false) }
    val sorted = remember(chapters, descending, unreadOnly) {
        visibleChapters(chapters, descending, unreadOnly)
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
                Box(Modifier.weight(1f)) {
                    ChapterHeading(sorted.size, descending, { descending = !descending }, copy,
                        unreadOnly, { unreadOnly = !unreadOnly })
                }
            }
            if (chapters.isEmpty()) {
                if (matching) ChaptersLoading(copy) else CatalogEmptyState(copy.chaptersEmptyTitle, copy.chaptersEmptyMessage)
            } else if (sorted.isEmpty()) {
                CatalogEmptyState(copy.unreadEmptyTitle, copy.unreadEmptyMessage)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(sorted, key = { it.chapter.id }, contentType = { "chapter" }) { entry ->
                        ChapterRow(entry, onRead, onToggleRead, copy, Modifier.padding(horizontal = 12.dp, vertical = 3.dp), onMarkReadThrough)
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
            item(key = "hero", contentType = "hero") { TwoPaneHero(manga, copy) }
            item(key = "actions", contentType = "actions") {
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
            item(key = "metadata", contentType = "metadata") { MetadataBlock(manga, copy, adaptations) }
            if (adaptations.isNotEmpty()) {
                item(key = "adaptations", contentType = "adaptations") { AdaptationBadges(adaptations, copy) }
            }
            item(key = "synopsis", contentType = "synopsis") {
                Text(
                    manga.synopsis ?: copy.synopsisMissing,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item(key = "read", contentType = "read") { ReadNowButton(chapters, onContinue, onRead, copy) }
            item(key = "connections", contentType = "connections") {
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
        // Landscape artwork stays crisp; absent or failed artwork uses a clean mirror.
        MangaBackdrop(manga.bannerUrl, manga.coverUrl, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    // Keep the sharp art visible above the title block: the
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
                    .aspectRatio(PosterAspectRatio)
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
        MangaBackdrop(banner, manga.coverUrl, Modifier.matchParentSize())
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
            MangaArtwork(manga.coverUrl, manga.title, Modifier.width(150.dp).aspectRatio(PosterAspectRatio).clip(RoundedCornerShape(16.dp)), copy.catalog)
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
private fun MetadataBlock(manga: Manga, copy: DetailCopy, adaptations: List<WatchableAdaptation>) {
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
            // v8.2.0 tier model: the content tier leads the badge row (16+ / 18+).
            TierRatingBadge(manga.contentRating, copy.catalog)
            detailAdaptationKinds(adaptations).forEach { kind ->
                YomiraBadge(text = copy.adaptationLabel(kind), icon = Icons.Default.Tv, accent = NeonLavender)
            }
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

/** Informational only: watchable entries cannot be opened as readable manga. */
@Composable
private fun AdaptationBadges(adaptations: List<WatchableAdaptation>, copy: DetailCopy) {
    if (adaptations.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        adaptations.forEach { adaptation ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = NeonViolet.copy(alpha = .16f),
                border = BorderStroke(1.dp, NeonLavender.copy(alpha = .35f)),
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Tv, null, Modifier.size(24.dp), tint = NeonLavender)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(copy.adaptationLabel(adaptation.kind), color = NeonLavender,
                            style = MaterialTheme.typography.labelLarge)
                        Text(adaptation.title, color = TextPrimary, style = MaterialTheme.typography.titleSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            adaptation.format?.let { YomiraBadge(text = it) }
                            adaptation.episodes?.let { YomiraBadge(text = String.format(copy.adaptationEpisodes, it)) }
                            adaptation.releaseYear?.let { YomiraBadge(text = it.toString()) }
                            adaptation.score?.let {
                                YomiraBadge(text = "%.1f".format(it), icon = Icons.Default.Star,
                                    accent = RatingGold, contentColor = RatingGold)
                            }
                        }
                    }
                }
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
        Text(if (started) copy.readContinue else copy.readNow, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ChapterHeading(
    count: Int,
    descending: Boolean,
    onToggle: () -> Unit,
    copy: DetailCopy,
    unreadOnly: Boolean,
    onToggleUnread: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(String.format(copy.chaptersHeading, count), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = unreadOnly,
                onClick = onToggleUnread,
                label = { Text(copy.unreadOnly) },
                leadingIcon = if (unreadOnly) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null,
            )
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
                    Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(15.dp), tint = NeonLavender)
                    Text(
                        if (descending) copy.sortNewest else copy.sortOldest,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Read chapters dim as a whole; unread chapters retain a crisp lavender outline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    entry: ChapterUiState,
    onRead: (Chapter) -> Unit,
    onToggleRead: (Chapter) -> Unit,
    copy: DetailCopy,
    modifier: Modifier = Modifier,
    onMarkReadThrough: (Chapter) -> Unit,
) {
    var showActions by remember(entry.chapter.id) { mutableStateOf(false) }
    if (showActions) QuickActionSheet(
        entry.chapter.title ?: String.format(copy.chapterFallback, entry.chapter.chapterNumber),
        { showActions = false },
        listOf(
            (if (entry.read) copy.markUnread else copy.markRead) to { onToggleRead(entry.chapter) },
            copy.markReadThrough to { onMarkReadThrough(entry.chapter) },
        ),
    )
    YomiraCard(
        modifier = modifier.fillMaxWidth().alpha(if (entry.read) 0.38f else 1.0f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (entry.read) HairlineBorder else NeonLavender.copy(alpha = .65f), RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onRead(entry.chapter) }, onLongClick = { showActions = true },
            ),
        shape = RoundedCornerShape(16.dp),
        containerColor = if (entry.read) Color.Transparent else CardSurface,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = { onToggleRead(entry.chapter) },
                shape = CircleShape,
                color = if (entry.read) SurfaceRaised else NeonPurple.copy(alpha = .18f),
                border = BorderStroke(
                    1.dp,
                    if (entry.read) HairlineBorder else NeonLavender.copy(alpha = .65f),
                ),
                tonalElevation = 0.dp,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (entry.read) {
                        Icon(Icons.Default.Check, copy.markUnread, Modifier.size(17.dp), tint = TextMuted)
                    } else {
                        Text(
                            chapterNumberLabel(entry.chapter.chapterNumber),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonLavender,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.chapter.title ?: String.format(copy.chapterFallback, entry.chapter.chapterNumber),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (entry.read) FontWeight.Normal else FontWeight.Bold,
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
                }
            }
            if (entry.read) {
                Surface(color = SurfaceRaised, shape = RoundedCornerShape(percent = 50)) {
                    Text(
                        copy.readLabel,
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
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
                        if (started) copy.readContinue else copy.readNow,
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
@Immutable
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
            items(entries, key = { it.id }, contentType = { "series" }) { entry ->
                Column(Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpenSeries(entry.id, entry.title) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        MangaArtwork(entry.coverUrl, entry.title, Modifier.fillMaxWidth().aspectRatio(PosterAspectRatio).clip(RoundedCornerShape(14.dp)), copy.catalog)
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
private fun ChaptersLoading(copy: DetailCopy) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonLavender)
        Text(copy.chaptersLoading, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
