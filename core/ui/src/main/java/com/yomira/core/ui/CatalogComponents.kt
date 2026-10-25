package com.yomira.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.ui.theme.*
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun MangaArtwork(url: String?, title: String, modifier: Modifier = Modifier, copy: CatalogCopy = CatalogCopy()) {
    SubcomposeAsyncImage(
        model = url, contentDescription = title, modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { Box(Modifier.fillMaxSize().background(CardSurface), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } },
        error = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(CardSurface, NeonPurple.copy(alpha = .25f)))), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = copy.noCoverArtwork, modifier = Modifier.size(40.dp), tint = TextMuted)
        } },
    )
}

/**
 * Weekly trending rotation for the hero showcase.
 *
 * The hero must never go stale: the same trending feed presents a different lead
 * card every ISO week, so the showcase changes "with the week" while staying fully
 * deterministic — the same week always renders the same order, which keeps tests,
 * screenshots and the user's memory of the shelf stable inside one week.
 */
fun weeklyTrendOrder(items: List<Manga>, today: LocalDate = LocalDate.now()): List<Manga> {
    if (items.size < 2) return items
    val week = today.get(WeekFields.ISO.weekOfWeekBasedYear())
    val offset = (week % items.size + items.size) % items.size
    return items.drop(offset) + items.take(offset)
}

/**
 * Cinematic hero carousel. When a series carries a real 16:9 landscape banner
 * (`bannerUrl` from AniList / Kitsu) the banner is shown sharp at its native
 * cinematic ratio under an elegant bottom gradient scrim; a portrait cover is only
 * blurred into the backdrop as a fallback when no banner exists.
 */
@Composable
fun HeroCarousel(manga: List<Manga>, onRead: (Manga) -> Unit, modifier: Modifier = Modifier, vault: Boolean = false, weeklyTrend: Boolean = true, copy: CatalogCopy = CatalogCopy()) {
    if (manga.isEmpty()) return
    val lineup = remember(manga, weeklyTrend) { if (weeklyTrend) weeklyTrendOrder(manga) else manga }
    val pager = rememberPagerState(pageCount = { lineup.size })
    val accent = if (vault) NeonFuchsia else NeonPurple
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp, key = { lineup[it].id }) { index ->
            HeroBanner(lineup[index], accent, onRead, copy)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            repeat(lineup.size) { page ->
                Box(Modifier.size(if (pager.currentPage == page) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(8.dp)).background(if (pager.currentPage == page) accent else TextMuted.copy(alpha = .3f)))
            }
        }
    }
}

/** One hero stage: 16:9 landscape banner when available, blurred cover fallback otherwise. */
@Composable
private fun HeroBanner(item: Manga, accent: Color, onRead: (Manga) -> Unit, copy: CatalogCopy) {
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
        val banner = item.bannerUrl?.trim()?.takeIf { it.isNotEmpty() }
        // The landscape banner keeps its native 16:9 cinematic ratio; the portrait
        // fallback needs the taller stage so the text block fits beside the cover.
        val stageHeight = if (banner != null) (maxWidth * 9f / 16f).coerceIn(220.dp, 420.dp) else 340.dp
        val coverWidth = (maxWidth * .3f).coerceIn(64.dp, 112.dp)
        val roomy = stageHeight >= 260.dp
        Box(Modifier.fillMaxWidth().height(stageHeight)) {
            if (banner != null) {
                MangaArtwork(banner, item.title, Modifier.matchParentSize(), copy)
                // Cinematic scrim: the banner stays sharp, only the lower third darkens
                // so the title block reads over any artwork.
                Box(Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.34f to Color.Transparent,
                        0.72f to DeepOledBlack.copy(alpha = .62f),
                        1f to DeepOledBlack.copy(alpha = .96f),
                    ),
                ))
            } else {
                MangaArtwork(item.coverUrl, item.title, Modifier.matchParentSize().blur(24.dp), copy)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = .12f), DeepOledBlack.copy(alpha = .96f)))))
            }
            Row(
                Modifier.align(if (banner != null) Alignment.BottomCenter else Alignment.Center).padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = if (banner != null) Alignment.Bottom else Alignment.CenterVertically,
            ) {
                MangaArtwork(item.coverUrl, item.title, Modifier.width(coverWidth).aspectRatio(.66f).clip(RoundedCornerShape(16.dp)), copy)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item.score?.let { ScoreBadge(it, copy) }
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = if (roomy) 3 else 2, overflow = TextOverflow.Ellipsis)
                    if (roomy) Text(item.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (roomy && item.contentRating == ContentRating.MATURE) Text(copy.matureBadgeLong, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Button(onClick = { onRead(item) }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
                        Text(copy.readNow)
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreBadge(score: Double, copy: CatalogCopy = CatalogCopy()) {
    Surface(color = CardSurface.copy(alpha = .6f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Star, contentDescription = copy.scoreDescription, tint = RatingGold, modifier = Modifier.size(14.dp))
            Text(String.format(Locale.getDefault(), "%.1f", score), style = MaterialTheme.typography.labelMedium, color = RatingGold)
        }
    }
}

@Composable
fun MangaCard(manga: Manga, columns: Int, onClick: () -> Unit, modifier: Modifier = Modifier, copy: CatalogCopy = CatalogCopy()) {
    // Phones render a single hero-sized column, tablets up to six, the Windows
    // desktop wall owns its own 6–8 column poster grid.
    require(columns in 1..8) { "Poster grid supports 1–8 columns, got $columns" }
    val roomy = columns <= 2
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            MangaArtwork(manga.coverUrl, manga.title, Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(14.dp)), copy)
            if (manga.contentRating == ContentRating.MATURE) {
                Surface(Modifier.align(Alignment.TopStart).padding(6.dp), color = CardSurface.copy(alpha = .9f), shape = RoundedCornerShape(6.dp)) {
                    Text(if (roomy) copy.matureBadgeLong else copy.matureBadgeShort, Modifier.padding(5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (columns <= 4) manga.score?.let { Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { ScoreBadge(it, copy) } }
        }
        Text(
            manga.title,
            style = when {
                columns == 1 -> MaterialTheme.typography.titleMedium
                columns == 2 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.labelMedium
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (roomy && manga.genres.isNotEmpty()) Text(manga.genres.take(2).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridColumnSelector(columns: Int, onColumnsChange: (Int) -> Unit, modifier: Modifier = Modifier, copy: CatalogCopy = CatalogCopy()) {
    SingleChoiceSegmentedButtonRow(modifier) {
        (2..4).forEachIndexed { index, count ->
            SegmentedButton(selected = columns == count, onClick = { onColumnsChange(count) }, shape = SegmentedButtonDefaults.itemShape(index, 3),
                modifier = Modifier.semantics { contentDescription = copy.columnDescriptions.getOrElse(index) { "$count" } }) { Text("$count") }
        }
    }
}

enum class MainDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Ana sayfa", Icons.Default.Home),
    DISCOVER("discover", "Keşfet", Icons.Default.Explore),
    LIBRARY("library", "Kitaplık", Icons.AutoMirrored.Filled.LibraryBooks),
    PROFILE("profile", "Yolculuk", Icons.Default.Insights),
    VAULT("vault", "+18 Vault", Icons.Default.Lock),
}

/**
 * Localized labels for every destination. [copy] carries the app's live locale;
 * when omitted the enum defaults are used (module previews and tests). Shared by
 * the bottom bar and the tablet rail so both spell destinations identically.
 */
internal fun navigationLabels(copy: CatalogCopy): Map<MainDestination, String> = mapOf(
    MainDestination.HOME to copy.navHome,
    MainDestination.DISCOVER to copy.navDiscover,
    MainDestination.LIBRARY to copy.navLibrary,
    MainDestination.PROFILE to copy.navProfile,
    MainDestination.VAULT to copy.navVault,
)

/**
 * Bottom navigation. [copy] carries the localized destination labels; when omitted the
 * enum defaults are used (module previews and tests). [vaultVisible] mirrors the settings
 * switch: a hidden vault is removed from the bar entirely, not merely locked.
 */
@Composable
fun YomiraBottomNavBar(selected: String?, onSelect: (MainDestination) -> Unit, copy: CatalogCopy = CatalogCopy(), vaultVisible: Boolean = true) {
    val labels = navigationLabels(copy)
    NavigationBar(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp)), containerColor = CardSurface.copy(alpha = .9f), tonalElevation = 0.dp) {
        MainDestination.entries.filter { vaultVisible || it != MainDestination.VAULT }.forEach { destination ->
            val label = labels.getValue(destination)
            NavigationBarItem(selected = selected == destination.route, onClick = { onSelect(destination) }, icon = { Icon(destination.icon, contentDescription = label) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonPurple, indicatorColor = NeonPurple.copy(alpha = .16f)))
        }
    }
}

/**
 * Tablet / foldable navigation rail. Replaces the bottom bar from 600dp upwards,
 * so the wide layout spends its height on content instead of a stretched phone bar.
 * [vaultVisible] mirrors the settings switch exactly like the bottom bar.
 */
@Composable
fun YomiraNavigationRail(
    selected: String?,
    onSelect: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    copy: CatalogCopy = CatalogCopy(),
    vaultVisible: Boolean = true,
) {
    val labelFor: (MainDestination) -> String = { destination ->
        when (destination) {
            MainDestination.HOME -> copy.navHome
            MainDestination.DISCOVER -> copy.navDiscover
            MainDestination.LIBRARY -> copy.navLibrary
            MainDestination.PROFILE -> copy.navProfile
            MainDestination.VAULT -> copy.navVault
        }
    }
    NavigationRail(
        modifier = modifier.width(96.dp),
        containerColor = CardSurface.copy(alpha = .92f),
        header = {
            Surface(color = NeonPurple.copy(alpha = .16f), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Y",
                    Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonPurple,
                )
            }
        },
    ) {
        Spacer(Modifier.height(18.dp))
        MainDestination.entries.filter { vaultVisible || it != MainDestination.VAULT }.forEach { destination ->
            val label = labelFor(destination)
            NavigationRailItem(
                selected = selected == destination.route,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = label) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = NeonPurple,
                    selectedTextColor = NeonPurple,
                    indicatorColor = NeonPurple.copy(alpha = .16f),
                ),
            )
        }
    }
}

/**
 * The endless-scroll footer: a pulsing neon ring plus a short status line.
 * Shared by Home/Discover and the +18 Vault so every feed communicates the
 * same "more is on the way" state without a layout jump.
 */
@Composable
fun NeonPagingIndicator(
    modifier: Modifier = Modifier,
    message: String = "Daha fazla seri yükleniyor…",
    accent: Color = NeonPurple,
) {
    val transition = rememberInfiniteTransition(label = "neon-paging")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "neon-paging-pulse",
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(30.dp).scale(pulse),
            color = accent.copy(alpha = pulse),
            trackColor = accent.copy(alpha = .16f),
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.labelLarge,
            color = accent.copy(alpha = .55f + pulse * .45f),
        )
    }
}

@Composable
fun CatalogEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}