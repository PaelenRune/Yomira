package com.yomira.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = YomiraColors.TextMuted,
    )
}

@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = YomiraColors.TextMuted,
    leading: ImageVector? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(YomiraColors.SurfaceRaised.copy(alpha = 0.72f))
            .border(1.dp, YomiraColors.OutlineSoft.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leading != null) Icon(leading, null, tint = accent, modifier = Modifier.size(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 38.dp,
    tint: Color = YomiraColors.TextPrimary,
    background: Color = YomiraColors.SurfaceRaised.copy(alpha = 0.6f),
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> YomiraColors.Outline.copy(alpha = 0.3f)
            hovered -> YomiraColors.Purple.copy(alpha = 0.85f)
            else -> YomiraColors.OutlineSoft.copy(alpha = 0.8f)
        },
        animationSpec = tween(160),
        label = "bubble-border",
    )
    val fillColor by animateColorAsState(
        targetValue = when {
            !enabled -> background.copy(alpha = 0.25f)
            hovered -> YomiraColors.SurfaceRaised
            else -> background
        },
        animationSpec = tween(160),
        label = "bubble-fill",
    )
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fillColor)
            .border(1.dp, borderColor, CircleShape)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> YomiraColors.TextMuted.copy(alpha = 0.5f)
                hovered -> YomiraColors.Lavender
                else -> tint
            },
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Primary pill with the brand gradient. Used only for the one main action of a surface. */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) {
                    BrandGradient
                } else {
                    Brush.horizontalGradient(listOf(YomiraColors.SurfaceRaised, YomiraColors.SurfaceRaised))
                },
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Color.White else YomiraColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else YomiraColors.TextMuted,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val border by animateColorAsState(
        targetValue = if (hovered) YomiraColors.Purple.copy(alpha = 0.75f) else YomiraColors.OutlineSoft,
        animationSpec = tween(160),
        label = "ghost-border",
    )
    val fill by animateColorAsState(
        targetValue = if (hovered) YomiraColors.SurfaceRaised else YomiraColors.Surface.copy(alpha = 0.45f),
        animationSpec = tween(160),
        label = "ghost-fill",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(50))
            .background(fill)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = YomiraColors.TextPrimary, modifier = Modifier.size(15.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = YomiraColors.TextPrimary)
    }
}

/** Compact keyboard hint, e.g. `Ctrl` + `F` in the search field. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (accent) YomiraColors.Purple.copy(alpha = 0.18f) else YomiraColors.SurfaceHighest)
            .border(
                1.dp,
                if (accent) YomiraColors.Purple.copy(alpha = 0.45f) else YomiraColors.OutlineSoft,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, letterSpacing = 0.6.sp),
            color = if (accent) YomiraColors.Lavender else YomiraColors.TextMuted,
        )
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 22.dp, color: Color = YomiraColors.Purple) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = 2.dp,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 72.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(YomiraColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = YomiraColors.Purple, modifier = Modifier.size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = YomiraColors.TextPrimary)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = YomiraColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            NeonButton(actionLabel, onAction)
        }
    }
}

/** Transient, non-blocking message anchored to the bottom of the shell. */
@Composable
fun NoticePill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(YomiraColors.SurfaceRaised)
            .border(1.dp, YomiraColors.Outline, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = YomiraColors.TextPrimary)
    }
}

/** A hairline that only adds structure where two sections meet. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

/** Uppercase numeric HUD (`01 / 12`) with fixed-width digits. */
@Composable
fun NumericHud(text: String, modifier: Modifier = Modifier, color: Color = YomiraColors.TextPrimary) {
    Text(text, modifier = modifier, style = NumericStyle.copy(fontSize = 11.sp), color = color)
}

@Composable
fun LoadingBlock(text: String = "Yükleniyor...", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spinner(size = 32.dp)
        Text(text, color = YomiraColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MessageBlock(
    text: String,
    modifier: Modifier = Modifier,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text, color = YomiraColors.TextMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (retryText != null && onRetry != null) {
            NeonButton(retryText, onRetry)
        }
    }
}

@Composable
fun HintFooter(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().background(YomiraColors.Surface.copy(alpha = 0.5f)).padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = YomiraColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, accent: Color = YomiraColors.TextPrimary) {
    MetaChip(text = text, modifier = modifier, accent = accent)
}

@Composable
fun ScoreBadge(score: Double?, modifier: Modifier = Modifier) {
    if (score == null) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(YomiraColors.Gold.copy(alpha = 0.15f))
            .border(1.dp, YomiraColors.Gold.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("★", color = YomiraColors.Gold, fontSize = 11.sp)
        Text(String.format(java.util.Locale.US, "%.1f", score), color = YomiraColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScoreBadge(score: Float?, modifier: Modifier = Modifier) {
    if (score == null) return
    ScoreBadge(score.toDouble(), modifier)
}

val Icons.Filled.ExternalLink: ImageVector
    get() = Icons.Default.Share
