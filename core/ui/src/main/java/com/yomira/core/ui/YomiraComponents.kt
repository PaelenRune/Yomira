package com.yomira.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.ui.theme.CardSurface
import com.yomira.core.ui.theme.HairlineBorder
import com.yomira.core.ui.theme.NeonLavender
import com.yomira.core.ui.theme.NeonPurple
import com.yomira.core.ui.theme.SurfaceRaised
import com.yomira.core.ui.theme.TextMuted
import com.yomira.core.ui.theme.TextPrimary
import com.yomira.core.ui.theme.TextSecondary

/**
 * Borderless surface with 16dp corners and soft elevation. [glow] gives
 * featured content a slightly stronger shadow without framing the artwork.
 */
@Composable
fun YomiraCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = CardSurface,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            shadowElevation = if (glow) 6.dp else 4.dp,
            tonalElevation = 0.dp,
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            shadowElevation = if (glow) 6.dp else 4.dp,
            tonalElevation = 0.dp,
        ) {
            Column(content = content)
        }
    }
}

/**
 * Minimalist capsule badge (score, maturity, genre, status). The accent only
 * tints the 1dp hairline and the icon, keeping dense metadata calm.
 */
@Composable
fun YomiraBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = NeonLavender,
    containerColor: Color = SurfaceRaised.copy(alpha = .82f),
    contentColor: Color = TextSecondary,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
            }
            Text(text, style = style, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Capsule filter chip used by the tag matrix. Selected chips get a translucent
 * violet fill and a lavender label instead of Material's solid secondary
 * container, so twelve selected tags never overpower the artwork.
 */
@Composable
fun YomiraFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
        } else {
            null
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) NeonPurple.copy(alpha = .45f) else HairlineBorder,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = TextMuted,
            iconColor = TextMuted,
            selectedContainerColor = NeonPurple.copy(alpha = .18f),
            selectedLabelColor = TextPrimary,
            selectedLeadingIconColor = NeonLavender,
        ),
    )
}

/**
 * Floating capsule container for reading controls (page scrubber, chapter
 * actions) and other transient chrome. It fakes frosted glass with a
 * translucent raised-surface gradient, a violet hairline and a soft elevation
 * shadow — Compose cannot blur the backdrop behind a composable, so this reads
 * as glass against artwork without a costly blur pass.
 */
@Composable
fun YomiraFloatingPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = .18f)),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceRaised.copy(alpha = .94f),
                            CardSurface.copy(alpha = .96f),
                        ),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
