package com.yomira.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.ui.theme.*
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Poster artwork with a designed fallback instead of an empty box.
 *
 * Missing artwork keeps a readable title and a quiet OLED gradient, without
 * introducing publication-format labels onto the poster.
 */
@Composable
fun MangaArtwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    copy: CatalogCopy = CatalogCopy(),
) {
    SubcomposeAsyncImage(
        model = url, contentDescription = title, modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { ArtworkPlaceholder(title, copy, loading = true) },
        error = { ArtworkPlaceholder(title, copy, loading = false) },
    )
}

/** Shared loading/error face of [MangaArtwork]: gradient and title. */
@Composable
private fun ArtworkPlaceholder(
    title: String,
    copy: CatalogCopy,
    loading: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 120.dp
        val accent = NeonLavender
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(
                    colors = listOf(
                        DeepOledBlack,
                        CardSurface,
                        NeonViolet.copy(alpha = .38f),
                        NeonPurple.copy(alpha = .22f),
                    ),
                ),
            ),
        )
        // Soft plum halo: gives the flat fallback the depth of a real poster.
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = .26f), Color.Transparent),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().padding(if (compact) 7.dp else 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier)
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(if (compact) 13.dp else 18.dp),
                        strokeWidth = 1.5.dp,
                        color = accent.copy(alpha = .8f),
                        trackColor = Color.Transparent,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = copy.noCoverArtwork,
                        modifier = Modifier.size(if (compact) 13.dp else 18.dp),
                        tint = TextMuted.copy(alpha = .7f),
                    )
                }
            }
            Text(
                title,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
 * reflected sharply into the OLED canvas as a fallback when no banner exists.
 */
@Composable
fun HeroCarousel(manga: List<Manga>, onRead: (Manga) -> Unit, modifier: Modifier = Modifier, vault: Boolean = false, weeklyTrend: Boolean = true, copy: CatalogCopy = CatalogCopy()) {
    if (manga.isEmpty()) return
    val lineup = remember(manga, weeklyTrend) { if (weeklyTrend) weeklyTrendOrder(manga) else manga }
    val pager = rememberPagerState(pageCount = { lineup.size })
    val accent = if (vault) NeonLavender else NeonPurple
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Every page shares one stage height. A pager is a fill-height scroller, so
        // leaving it unbounded inside a lazy list swallowed the leftover space and
        // pushed the dots away from the card; pinning the height makes the carousel
        // a single tidy block on phones and tablets alike.
        val anyBanner = lineup.any { !it.bannerUrl.isNullOrBlank() }
        val contentWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
        val stageHeight = if (anyBanner) (contentWidth * 9f / 16f).coerceIn(210.dp, 420.dp) else 340.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.height(stageHeight),
                key = { lineup[it].id },
            ) { index ->
                HeroBanner(lineup[index], accent, stageHeight, onRead, copy)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                repeat(lineup.size) { page ->
                    Box(Modifier.size(if (pager.currentPage == page) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(8.dp)).background(if (pager.currentPage == page) accent else TextMuted.copy(alpha = .3f)))
                }
            }
        }
    }
}

/** One hero stage: landscape artwork or a sharp, vertically mirrored poster. */
@Composable
private fun HeroBanner(item: Manga, accent: Color, stageHeight: Dp, onRead: (Manga) -> Unit, copy: CatalogCopy) {
    val heroShape = MaterialTheme.shapes.medium
    BoxWithConstraints(Modifier.fillMaxWidth().height(stageHeight).shadow(4.dp, heroShape).clip(heroShape)) {
        val banner = item.bannerUrl?.trim()?.takeIf { it.isNotEmpty() }
        val coverWidth = (maxWidth * .3f).coerceIn(64.dp, 112.dp)
        val roomy = stageHeight >= 260.dp
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            if (banner != null) {
                MangaBackdrop(banner, item.coverUrl, Modifier.matchParentSize())
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
                MangaBackdrop(null, item.coverUrl, Modifier.matchParentSize())
            }
            Row(
                Modifier.align(if (banner != null) Alignment.BottomCenter else Alignment.Center).padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = if (banner != null) Alignment.Bottom else Alignment.CenterVertically,
            ) {
                Box(Modifier.width(coverWidth).aspectRatio(.66f).clip(MaterialTheme.shapes.medium)) {
                    MangaArtwork(item.coverUrl, item.title, Modifier.matchParentSize(), copy)
                    AgeRatingBadge(item.contentRating, Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        item.score?.let { ScoreBadge(it, copy) }
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = if (roomy) 3 else 2, overflow = TextOverflow.Ellipsis)
                    if (roomy) Text(item.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (roomy && item.contentRating == ContentRating.MATURE) Text(copy.matureBadgeLong, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    Button(
                        onClick = { onRead(item) },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(copy.readNow, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreBadge(score: Double, copy: CatalogCopy = CatalogCopy()) {
    YomiraBadge(
        text = String.format(Locale.getDefault(), "%.1f", score),
        icon = Icons.Default.Star,
        accent = RatingGold,
        containerColor = DeepOledBlack.copy(alpha = .58f),
        contentColor = RatingGold,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun MangaCard(manga: Manga, columns: Int, onClick: () -> Unit, modifier: Modifier = Modifier, copy: CatalogCopy = CatalogCopy()) {
    // Phones render a single hero-sized column, tablets up to six, the Windows
    // desktop wall owns its own 6–8 column poster grid.
    require(columns in 1..8) { "Poster grid supports 1–8 columns, got $columns" }
    val cardShape = MaterialTheme.shapes.medium
    Box(
        modifier.shadow(4.dp, cardShape).clip(cardShape).clickable(onClick = onClick).semantics(mergeDescendants = true) {},
    ) {
        MangaArtwork(manga.coverUrl, manga.title, Modifier.fillMaxWidth().aspectRatio(.68f), copy)
        AgeRatingBadge(manga.contentRating, Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

/** The current model distinguishes adult content, but does not provide a 16+ tier. */
internal fun posterAgeLabel(rating: ContentRating): String? = when (rating) {
    ContentRating.SAFE -> null
    ContentRating.MATURE, ContentRating.EXPLICIT -> "+18"
}

/** The only overlay allowed on a series poster: a small translucent age label. */
@Composable
private fun AgeRatingBadge(rating: ContentRating, modifier: Modifier = Modifier) {
    val label = posterAgeLabel(rating) ?: return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = .58f),
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
    ) {
        Text(label, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridColumnSelector(columns: Int, onColumnsChange: (Int) -> Unit, modifier: Modifier = Modifier, copy: CatalogCopy = CatalogCopy()) {
    SingleChoiceSegmentedButtonRow(modifier) {
        (2..4).forEachIndexed { index, count ->
            SegmentedButton(
                selected = columns == count,
                onClick = { onColumnsChange(count) },
                shape = SegmentedButtonDefaults.itemShape(index, 3),
                modifier = Modifier.semantics { contentDescription = copy.columnDescriptions.getOrElse(index) { "$count" } },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = NeonPurple,
                    activeContentColor = Color.White,
                    activeBorderColor = NeonPurple,
                    inactiveContainerColor = CardSurface,
                    inactiveContentColor = TextMuted,
                    inactiveBorderColor = HairlineBorder,
                ),
            ) { Text("$count", style = MaterialTheme.typography.labelMedium) }
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
 * Floating capsule bottom navigation.
 *
 * The bar is inset from the screen edge, clipped to a 32dp capsule and drawn as
 * a translucent raised-surface gradient inside a violet hairline — the Cstation /
 * Webtoon Reader "floating pill" treatment. [copy] carries the localized
 * destination labels; [vaultVisible] mirrors the settings switch: a hidden vault
 * is removed from the bar entirely, not merely locked.
 */
@Composable
fun YomiraBottomNavBar(selected: String?, onSelect: (MainDestination) -> Unit, copy: CatalogCopy = CatalogCopy(), vaultVisible: Boolean = true) {
    val labels = navigationLabels(copy)
    val capsule = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = capsule,
            color = Color.Transparent,
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = .2f)),
            shadowElevation = 16.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceRaised.copy(alpha = .93f),
                            CardSurface.copy(alpha = .96f),
                        ),
                    ),
                ),
            ) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    MainDestination.entries.filter { vaultVisible || it != MainDestination.VAULT }.forEach { destination ->
                        val label = labels.getValue(destination)
                        NavigationBarItem(
                            selected = selected == destination.route,
                            onClick = { onSelect(destination) },
                            icon = { Icon(destination.icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonLavender,
                                selectedTextColor = NeonLavender,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = NeonPurple.copy(alpha = .2f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tablet / foldable navigation rail. Replaces the bottom bar from 600dp upwards,
 * so the wide layout spends its height on content instead of a stretched phone bar.
 * Like the phone capsule it floats: a soft 28dp card with a hairline border and a
 * gradient brand tile instead of Material's flat rail. [vaultVisible] mirrors the
 * settings switch exactly like the bottom bar.
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
    Box(modifier.fillMaxHeight().padding(horizontal = 4.dp, vertical = 10.dp)) {
        Surface(
            modifier = Modifier.width(88.dp).fillMaxHeight(),
            shape = RoundedCornerShape(28.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, HairlineBorder),
            tonalElevation = 0.dp,
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0, 0, 0, 0),
                header = {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(NeonPurple, NeonLavender))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Y",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
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
                        icon = { Icon(destination.icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = NeonLavender,
                            selectedTextColor = NeonLavender,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = NeonPurple.copy(alpha = .2f),
                        ),
                    )
                }
            }
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NeonPurple.copy(alpha = .12f),
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = .25f)),
            tonalElevation = 0.dp,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = NeonLavender,
                modifier = Modifier.padding(14.dp).size(32.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
