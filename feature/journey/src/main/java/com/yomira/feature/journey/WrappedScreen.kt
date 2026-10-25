package com.yomira.feature.journey

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.core.ui.theme.RatingGold
import com.yomira.domain.journey.BadgeTier
import com.yomira.domain.journey.WrappedPeriod
import com.yomira.domain.journey.WrappedSummary
import kotlinx.coroutines.launch

private data class WrappedSlide(
    val title: String,
    val accent: Color,
    val secondary: Color,
)

/**
 * Spotify-Wrapped style cinematic recap.
 *
 * Five horizontally paged story cards over an animated gradient, plus a
 * dedicated share action that renders a platform-independent 1080×1920 card.
 * The screen never talks to the network; the summary it shows was computed from
 * the local Room ledger.
 */
@Composable
fun WrappedScreen(
    state: WrappedUiState,
    onClose: () -> Unit,
    onPeriodChange: (WrappedPeriod) -> Unit,
    copy: WrappedCopy = WrappedCopy(),
    modifier: Modifier = Modifier,
) {
    val summary = state.summary
    var shareError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.period) {
        if (state.summary == null && !state.loading) onPeriodChange(state.period)
    }

    Box(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(Color(0xFF07040F)),
    ) {
        CinematicGlow()
        when {
            state.loading && summary == null -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = Color(0xFFFF4FA3),
            )
            summary == null -> WrappedEmpty(onClose, onPeriodChange, state.period, copy)
            summary.isEmpty -> WrappedEmpty(onClose, onPeriodChange, state.period, copy)
            else -> WrappedStory(
                summary = summary,
                copy = copy,
                onClose = onClose,
                onPeriodChange = onPeriodChange,
                onShare = {
                    scope.launch {
                        shareError = WrappedShareCard.share(context, summary, copy).isFailure
                    }
                },
            )
        }
        if (shareError) {
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(20.dp),
                color = Color(0xFF421021),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(copy.shareFailed, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD9E4))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { shareError = false }) { Text(copy.close, color = Color(0xFFFF8FB8)) }
                }
            }
        }
    }
}

@Composable
private fun CinematicGlow() {
    val transition = rememberInfiniteTransition(label = "wrapped-glow")
    val pulse by transition.animateFloat(
        initialValue = .72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
        label = "wrapped-glow-pulse",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF7A0B52).copy(alpha = .55f * pulse), Color.Transparent),
                    radius = 1400f,
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xFF05030B).copy(alpha = .92f)),
                ),
            ),
    )
}

@Composable
private fun WrappedStory(
    summary: WrappedSummary,
    copy: WrappedCopy,
    onClose: () -> Unit,
    onPeriodChange: (WrappedPeriod) -> Unit,
    onShare: () -> Unit,
) {
    val slides = remember(summary) {
        listOf(
            WrappedSlide(copy.periodLabel(summary), Color(0xFFFF2E93), Color(0xFF9D4EDD)),
            WrappedSlide(copy.timeHeadline.format(copy.formatDuration(summary.activeMillis)), Color(0xFF42D9C8), Color(0xFF2A6BFF)),
            WrappedSlide(copy.topGenreHeadline, Color(0xFFFFB703), Color(0xFFFF5E3A)),
            WrappedSlide(copy.growthHeadline, Color(0xFFB388FF), Color(0xFF7C4DFF)),
            WrappedSlide(copy.finalHeadline, Color(0xFFFF4FA3), Color(0xFF6A0B3D)),
        )
    }
    val pager = rememberPagerState(pageCount = { slides.size })

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) { page ->
            val slide = slides[page]
            val active = pager.currentPage == page
            WrappedSlideContent(
                summary = summary,
                copy = copy,
                slide = slide,
                page = page,
                active = active,
                onShare = onShare,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = copy.close, tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            PeriodPill(summary.period, copy, onPeriodChange)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onShare) {
                Icon(Icons.Default.IosShare, contentDescription = copy.share, tint = Color.White)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slides.indices.forEach { index ->
                Box(
                    Modifier
                        .width(if (index == pager.currentPage) 22.dp else 7.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (index == pager.currentPage) Color.White else Color.White.copy(alpha = .3f)),
                )
            }
        }
    }
}

@Composable
private fun PeriodPill(period: WrappedPeriod, copy: WrappedCopy, onPeriodChange: (WrappedPeriod) -> Unit) {
    Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(3.dp)) {
            PeriodChip(copy.monthly, period == WrappedPeriod.MONTHLY) { onPeriodChange(WrappedPeriod.MONTHLY) }
            PeriodChip(copy.yearly, period == WrappedPeriod.YEARLY) { onPeriodChange(WrappedPeriod.YEARLY) }
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color.White else Color.Transparent,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF4A0A2B) else Color.White.copy(alpha = .72f),
        )
    }
}

@Composable
private fun WrappedSlideContent(
    summary: WrappedSummary,
    copy: WrappedCopy,
    slide: WrappedSlide,
    page: Int,
    active: Boolean,
    onShare: () -> Unit,
) {
    val appear by animateFloatAsState(
        targetValue = if (active) 1f else .3f,
        animationSpec = tween(450),
        label = "wrapped-appear-$page",
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 30.dp, vertical = 96.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            copy.shareCardTag,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp),
            color = Color.White.copy(alpha = .55f * appear),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            copy.periodLabel(summary),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = .85f),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(30.dp))

        when (page) {
            0 -> ChaptersSlide(summary, copy, slide, appear)
            1 -> TimeSlide(summary, copy, slide)
            2 -> GenreSlide(summary, copy, slide)
            3 -> GrowthSlide(summary, copy, slide)
            else -> FinalSlide(summary, copy, onShare)
        }
    }
}

@Composable
private fun ChaptersSlide(summary: WrappedSummary, copy: WrappedCopy, slide: WrappedSlide, appear: Float) {
    GradientHeadline("${copy.formatNumber(summary.chapters)}", slide, appear)
    Text(
        if (summary.period == WrappedPeriod.YEARLY) copy.chaptersHeadlineYearly.format(summary.chapters)
        else copy.chaptersHeadline.format(summary.chapters),
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        copy.chaptersSubline.format(copy.formatNumber(summary.pages), summary.readingUnits),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = .65f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    if (summary.completedSeries > 0) {
        StatPill(Icons.Default.EmojiEvents, copy.completedSeries.format(summary.completedSeries), slide.accent)
    }
}

@Composable
private fun TimeSlide(summary: WrappedSummary, copy: WrappedCopy, slide: WrappedSlide) {
    Text(
        copy.formatDuration(summary.activeMillis),
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 68.sp),
        color = slide.accent,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
    Text(
        copy.timeHeadline.format(copy.formatDuration(summary.activeMillis)),
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        copy.timeSubline.format(summary.activeDays),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = .65f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(30.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatPill(Icons.Default.LocalFireDepartment, copy.formatNumber(summary.activeDays), slide.accent)
        StatPill(Icons.Default.AutoAwesome, "${summary.readingUnits}", slide.secondary)
    }
}

@Composable
private fun GenreSlide(summary: WrappedSummary, copy: WrappedCopy, slide: WrappedSlide) {
    Text(
        summary.topGenre ?: copy.topGenreEmpty,
        style = MaterialTheme.typography.displaySmall.copy(fontSize = 50.sp),
        color = slide.accent,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        copy.topGenreHeadline,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = .8f),
    )
    Spacer(Modifier.height(26.dp))
    summary.genreBreakdown.forEach { slice ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slice.genre,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = .85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    copy.formatNumber(slice.chapters),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = .55f),
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))) {
                Box(
                    Modifier
                        .fillMaxWidth(slice.fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(slide.accent, slide.secondary))),
                )
            }
        }
    }
    Spacer(Modifier.height(26.dp))
    Text(
        copy.topSeriesHeadline,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = .55f),
    )
    Text(
        summary.topSeries ?: copy.topSeriesEmpty,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GrowthSlide(summary: WrappedSummary, copy: WrappedCopy, slide: WrappedSlide) {
    val growth = summary.growthPercent
    Text(
        when {
            growth == null -> "∞"
            growth >= 0 -> "+%$growth%"
            else -> "%$growth%"
        },
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
        color = if (growth != null && growth < 0) Color(0xFFFF8FA3) else Color(0xFF55F0C1),
        fontWeight = FontWeight.Black,
    )
    Text(
        copy.growthHeadline,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = .8f),
    )
    if (growth == null) {
        Spacer(Modifier.height(10.dp))
        Text(copy.growthFirst, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .6f), textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(30.dp))
    Surface(color = Color.White.copy(alpha = .09f), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                copy.levelHeadline.format(
                    summary.level.level,
                    if (copy.english) summary.level.titleEn else summary.level.titleTr,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = slide.accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                copy.levelSubline.format(copy.formatNumber(summary.level.totalXp)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = .6f),
            )
        }
    }
}

@Composable
private fun FinalSlide(summary: WrappedSummary, copy: WrappedCopy, onShare: () -> Unit) {
    Text(
        copy.finalHeadline,
        style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp),
        color = Color.White,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        copy.finalBody,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = .68f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    Text(copy.badgesHeadline, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .55f))
    Spacer(Modifier.height(10.dp))
    if (summary.badgesEarned.isEmpty()) {
        Text(copy.badgesEmpty, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .6f), textAlign = TextAlign.Center)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.badgesEarned.distinct().take(4).forEach { tier ->
                Surface(color = tierColor(tier).copy(alpha = .18f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        if (copy.english) tier.labelEn else tier.labelTr,
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = tierColor(tier),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(36.dp))
    Button(
        onClick = onShare,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF6A0B3D)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(copy.finalCta, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    Text("${summary.chapters} · ${copy.formatDuration(summary.activeMillis)}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .4f))
}

@Composable
private fun GradientHeadline(value: String, slide: WrappedSlide, appear: Float) {
    Box(
        Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = .05f))
            .padding(horizontal = 34.dp, vertical = 12.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 92.sp),
            fontWeight = FontWeight.Black,
            color = slide.accent.copy(alpha = .45f + .55f * appear),
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color) {
    Surface(color = accent.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WrappedEmpty(
    onClose: () -> Unit,
    onPeriodChange: (WrappedPeriod) -> Unit,
    period: WrappedPeriod,
    copy: WrappedCopy,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFF4FA3), modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text(copy.emptyTitle, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(copy.emptyBody, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        PeriodPill(period, copy, onPeriodChange)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF4A0A2B))) {
            Text(copy.close)
        }
    }
}
