package com.yomira.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import java.util.Locale

@Composable
fun MangaArtwork(url: String?, title: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = url, contentDescription = title, modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { Box(Modifier.fillMaxSize().background(CardSurface), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } },
        error = { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(CardSurface, NeonPurple.copy(alpha = .25f)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MenuBook, contentDescription = "Kapak görseli yok", modifier = Modifier.size(40.dp), tint = TextMuted)
        } },
    )
}

@Composable
fun HeroCarousel(manga: List<Manga>, onRead: (Manga) -> Unit, modifier: Modifier = Modifier, vault: Boolean = false) {
    if (manga.isEmpty()) return
    val pager = rememberPagerState(pageCount = { manga.size })
    val accent = if (vault) NeonFuchsia else NeonPurple
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp, key = { manga[it].id }) { index ->
            val item = manga[index]
            Box(Modifier.fillMaxWidth().heightIn(min = 340.dp).clip(RoundedCornerShape(28.dp))) {
                MangaArtwork(item.bannerUrl ?: item.coverUrl, item.title, Modifier.matchParentSize().blur(24.dp))
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = .12f), DeepOledBlack.copy(alpha = .96f)))))
                Row(Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    MangaArtwork(item.coverUrl, item.title, Modifier.width(112.dp).aspectRatio(.66f).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item.score?.let { ScoreBadge(it) }
                        Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(item.genres.take(3).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        if (item.contentRating == ContentRating.MATURE) Text("16+ / Mature", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Button(onClick = { onRead(item) }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)) {
                            Text("Hemen oku")
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            repeat(manga.size) { page ->
                Box(Modifier.size(if (pager.currentPage == page) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(8.dp)).background(if (pager.currentPage == page) accent else TextMuted.copy(alpha = .3f)))
            }
        }
    }
}

@Composable
fun ScoreBadge(score: Double) {
    Surface(color = CardSurface.copy(alpha = .6f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Star, contentDescription = "Puan", tint = RatingGold, modifier = Modifier.size(14.dp))
            Text(String.format(Locale.getDefault(), "%.1f", score), style = MaterialTheme.typography.labelMedium, color = RatingGold)
        }
    }
}

@Composable
fun MangaCard(manga: Manga, columns: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    require(columns in 2..4)
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            MangaArtwork(manga.coverUrl, manga.title, Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(14.dp)))
            if (manga.contentRating == ContentRating.MATURE) {
                Surface(Modifier.align(Alignment.TopStart).padding(6.dp), color = CardSurface.copy(alpha = .9f), shape = RoundedCornerShape(6.dp)) {
                    Text(if (columns == 2) "16+ / Mature" else "16+", Modifier.padding(5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (columns < 4) manga.score?.let { Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { ScoreBadge(it) } }
        }
        Text(manga.title, style = if (columns == 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (columns == 2 && manga.genres.isNotEmpty()) Text(manga.genres.take(2).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridColumnSelector(columns: Int, onColumnsChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        (2..4).forEachIndexed { index, count ->
            SegmentedButton(selected = columns == count, onClick = { onColumnsChange(count) }, shape = SegmentedButtonDefaults.itemShape(index, 3),
                modifier = Modifier.semantics { contentDescription = when (count) { 2 -> "2 sütun, büyük ve detaylı"; 3 -> "3 sütun, standart"; else -> "4 sütun, kompakt koleksiyon" } }) { Text("$count") }
        }
    }
}

enum class MainDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Ana sayfa", Icons.Default.Home),
    DISCOVER("discover", "Keşfet", Icons.Default.Explore),
    LIBRARY("library", "Kitaplık", Icons.AutoMirrored.Filled.LibraryBooks),
    VAULT("vault", "+18 Vault", Icons.Default.Lock),
}

@Composable
fun YomiraBottomNavBar(selected: String?, onSelect: (MainDestination) -> Unit) {
    NavigationBar(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(28.dp)), containerColor = CardSurface.copy(alpha = .9f), tonalElevation = 0.dp) {
        MainDestination.entries.forEach { destination ->
            NavigationBarItem(selected = selected == destination.route, onClick = { onSelect(destination) }, icon = { Icon(destination.icon, contentDescription = destination.label) }, label = { Text(destination.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonPurple, indicatorColor = NeonPurple.copy(alpha = .16f)))
        }
    }
}

@Composable
fun CatalogEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.MenuBook, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
