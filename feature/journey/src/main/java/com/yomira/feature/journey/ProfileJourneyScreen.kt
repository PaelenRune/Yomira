package com.yomira.feature.journey

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.ui.theme.CardSurface
import com.yomira.core.ui.theme.DeepOledBlack
import com.yomira.core.ui.theme.NeonFuchsia
import com.yomira.core.ui.theme.NeonPurple
import com.yomira.core.ui.theme.RatingGold
import com.yomira.core.ui.theme.TextMuted
import com.yomira.core.ui.theme.TextPrimary
import com.yomira.domain.journey.BadgeCategory
import com.yomira.domain.journey.BadgeProgress
import com.yomira.domain.journey.BadgeTier
import com.yomira.domain.journey.DailyReading
import com.yomira.domain.journey.JourneyDashboard
import com.yomira.domain.journey.JourneyStats
import java.time.LocalDate

/**
 * Profile & statistics tab.
 *
 * Everything on this screen comes from [com.yomira.domain.journey.JourneyRepository]:
 * a local Room aggregate, never a network profile. The weekly chart is Monday to
 * Sunday, badges are grouped by the four product categories and locked tiers stay
 * visible as goals rather than disappearing.
 */
@Composable
fun ProfileJourneyScreen(
    state: ProfileUiState,
    onOpenWrapped: () -> Unit,
    onRefresh: () -> Unit = {},
    copy: JourneyCopy = JourneyCopy(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    Box(modifier.fillMaxSize().background(DeepOledBlack)) {
        when {
            state.dashboard == null && state.loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = NeonPurple,
            )
            state.dashboard == null -> Text(
                state.error ?: copy.emptyBody,
                Modifier.align(Alignment.Center).padding(28.dp),
                color = TextMuted,
            )
            else -> ProfileContent(state.dashboard, onOpenWrapped, copy)
        }
    }
}

@Composable
private fun ProfileContent(
    dashboard: JourneyDashboard,
    onOpenWrapped: () -> Unit,
    copy: JourneyCopy,
) {
    val stats = dashboard.stats
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ProfileHeader(copy) }
        item { LevelHero(stats, copy) }
        item { WeeklyCard(stats, copy) }
        item { StatsSection(stats, copy) }
        item { WrappedCallToAction(dashboard, onOpenWrapped, copy) }
        item { BadgesHeader(dashboard, copy) }

        BadgeCategory.entries.forEach { category ->
            val badges = dashboard.badges.filter { it.badge.category == category }
            if (badges.isNotEmpty()) {
                item(key = "header-${category.name}") { CategoryHeader(category, badges, copy) }
                item(key = "row-${category.name}") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(badges, key = { it.badge.id }) { progress ->
                            BadgeCard(progress, copy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(copy: JourneyCopy) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(copy.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(NeonPurple, NeonFuchsia))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Insights, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LevelHero(stats: JourneyStats, copy: JourneyCopy) {
    val level = stats.level
    val title = if (copy.english) level.titleEn else level.titleTr
    val progress by animateFloatAsState(level.progress, tween(800), label = "level-progress")
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2A1350), Color(0xFF4D0B3B), Color(0xFF1A1030)),
                    ),
                )
                .border(1.dp, NeonPurple.copy(alpha = .35f), RoundedCornerShape(28.dp))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${copy.levelPrefix} ${level.level} · $title",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            "${copy.formatNumber(level.totalXp)} ${copy.xpSuffix}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${level.level}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = NeonFuchsia,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = NeonFuchsia,
                    trackColor = Color.White.copy(alpha = .12f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (level.level >= com.yomira.domain.journey.LevelProgression.MAX_LEVEL) copy.maxLevel
                        else copy.xpToNext.format(
                            copy.formatNumber(level.xpRemaining),
                            if (copy.english) "level ${level.level + 1}" else "Seviye ${level.level + 1}",
                        ),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    StreakChip(stats.currentStreak, copy)
                }
                if (stats.currentStreak > 0) {
                    Text(
                        if (stats.streakGraceAvailable) copy.streakGrace else copy.streakNoGrace,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (stats.streakGraceAvailable) NeonPurple else TextMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.favoriteGenre?.let { genre ->
                        InfoChip("${copy.favoriteGenre}: $genre", NeonPurple)
                    }
                    if (stats.readingUnits > 0) {
                        InfoChip("${copy.formatNumber(stats.readingUnits)} ${copy.statUnits}", NeonFuchsia)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakChip(days: Int, copy: JourneyCopy) {
    Surface(color = NeonFuchsia.copy(alpha = .16f), shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = NeonFuchsia, modifier = Modifier.size(15.dp))
            Text(copy.streakDays.format(days), style = MaterialTheme.typography.labelMedium, color = NeonFuchsia, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoChip(text: String, accent: Color) {
    Surface(color = accent.copy(alpha = .14f), shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WeeklyCard(stats: JourneyStats, copy: JourneyCopy) {
    val maxChapters = stats.weekly.maxOfOrNull { it.chapters }?.coerceAtLeast(0) ?: 0
    val totalChapters = stats.weekly.sumOf { it.chapters }
    val totalMillis = stats.weekly.sumOf { it.activeMillis }
    Surface(color = CardSurface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(copy.weeklyTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (totalChapters > 0) "${copy.weeklyChapters.format(totalChapters)} · ${copy.formatDuration(totalMillis)}"
                    else copy.formatDuration(totalMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }
            if (totalChapters == 0) {
                Text(copy.weeklyEmpty, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Row(
                Modifier.fillMaxWidth().height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.weekly.forEach { day ->
                    WeeklyBar(
                        day = day,
                        maxChapters = maxChapters,
                        label = weekdayLabel(day, copy),
                        isToday = day.epochDay == LocalDate.now().toEpochDay(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyBar(
    day: DailyReading,
    maxChapters: Int,
    label: String,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val fraction = if (maxChapters <= 0) 0f else (day.chapters.toFloat() / maxChapters.toFloat()).coerceIn(0f, 1f)
    val animated by animateFloatAsState(fraction, tween(650), label = "weekly-$label")
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            if (day.chapters > 0) "${day.chapters}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) NeonFuchsia else TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height((84 * animated).coerceAtLeast(if (day.chapters > 0) 6f else 3f).dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    if (isToday) Brush.verticalGradient(listOf(NeonFuchsia, NeonPurple))
                    else Brush.verticalGradient(listOf(NeonPurple.copy(alpha = .75f), NeonPurple.copy(alpha = .28f))),
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isToday) TextPrimary else TextMuted)
    }
}

    private fun weekdayLabel(day: DailyReading, copy: JourneyCopy): String {
        val index = LocalDate.ofEpochDay(day.epochDay).dayOfWeek.value - 1
        return copy.weekdays.getOrElse(index) { "" }
    }

@Composable
private fun StatsSection(stats: JourneyStats, copy: JourneyCopy) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(copy.statsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(copy.statChapters, copy.formatNumber(stats.totalChapters), Modifier.weight(1f), NeonPurple)
            StatCard(copy.statPages, copy.formatNumber(stats.totalPages), Modifier.weight(1f), NeonFuchsia)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(copy.statTime, copy.formatDuration(stats.activeMillis), Modifier.weight(1f), Color(0xFF42D9C8))
            StatCard(copy.statSeries, copy.formatNumber(stats.completedSeries), Modifier.weight(1f), RatingGold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(copy.statDays, copy.formatNumber(stats.activeDays), Modifier.weight(1f), Color(0xFF7DE2FF))
            StatCard(copy.statLongest, "${stats.longestStreak}", Modifier.weight(1f), NeonFuchsia)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(copy.statGenres, copy.formatNumber(stats.distinctGenres), Modifier.weight(1f), NeonPurple)
            StatCard(copy.favoriteGenre, stats.favoriteGenre ?: "—", Modifier.weight(1f), Color(0xFFB48CFF))
        }
        Text(copy.unitHint, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, accent: Color) {
    Surface(modifier, color = CardSurface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WrappedCallToAction(dashboard: JourneyDashboard, onOpenWrapped: () -> Unit, copy: JourneyCopy) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF4A148C), Color(0xFFC2185B))))
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFE082), modifier = Modifier.size(18.dp))
                Text(copy.wrappedCtaTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(copy.wrappedCtaBody, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .8f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    copy.badgesCount.format(dashboard.unlockedCount, dashboard.totalBadgeCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = .75f),
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onOpenWrapped,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF8E0B4C)),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(copy.wrappedCtaButton, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun BadgesHeader(dashboard: JourneyDashboard, copy: JourneyCopy) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(copy.badgesTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(copy.badgesSubtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Text(
            copy.badgesCount.format(dashboard.unlockedCount, dashboard.totalBadgeCount),
            style = MaterialTheme.typography.labelMedium,
            color = NeonPurple,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CategoryHeader(category: BadgeCategory, badges: List<BadgeProgress>, copy: JourneyCopy) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
        Text(copy.categoryLabel(category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "${badges.count { it.isUnlocked }}/${badges.size}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

@Composable
private fun BadgeCard(progress: BadgeProgress, copy: JourneyCopy) {
    val accent = progress.currentTier?.let(::tierColor) ?: TextMuted
    val unlocked = progress.isUnlocked
    Surface(
        Modifier.width(172.dp),
        color = if (unlocked) CardSurface else CardSurface.copy(alpha = .68f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (unlocked) accent.copy(alpha = .55f) else Color.White.copy(alpha = .06f),
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (unlocked) .18f else .08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(progress.badge.emoji, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.weight(1f))
                if (!unlocked) {
                    Icon(Icons.Default.Lock, contentDescription = copy.locked, tint = TextMuted, modifier = Modifier.size(15.dp))
                } else {
                    Text(
                        copy.tierLabel(progress.currentTier ?: BadgeTier.BRONZE),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                copy.badgeTitle(progress.badge),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                copy.badgeDescription(progress.badge),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )
            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = if (unlocked) accent else NeonPurple.copy(alpha = .5f),
                trackColor = Color.White.copy(alpha = .08f),
            )
            val nextTarget = progress.nextTarget
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        progress.isFullyUnlocked -> copy.maxTier
                        nextTarget != null -> copy.progressTo.format(
                            minOf(progress.current, nextTarget),
                            nextTarget,
                        )
                        else -> copy.locked
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    BadgeTier.entries.forEach { tier ->
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (tier in progress.unlockedTiers) tierColor(tier) else Color.White.copy(alpha = .12f)),
                        )
                    }
                }
            }
        }
    }
}

fun tierColor(tier: BadgeTier): Color = when (tier) {
    BadgeTier.BRONZE -> Color(0xFFCD7F32)
    BadgeTier.SILVER -> Color(0xFFC9D1D9)
    BadgeTier.GOLD -> RatingGold
    BadgeTier.PLATINUM -> Color(0xFF7DE2FF)
}

/** Public for previews/tests: the tab opens with the same badge order as the product brief. */
fun categoryOrder(): List<BadgeCategory> = BadgeCategory.entries
