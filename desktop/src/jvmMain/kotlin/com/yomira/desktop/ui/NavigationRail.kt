package com.yomira.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.FeedSource

/**
 * Slim icon-first navigation rail. It is part of the wide-screen shell only:
 * icon-only at 1180dp+, labelled at 1680dp+, and gone on narrow windows where
 * the masthead carries every control.
 */
@Composable
fun YomiraRail(
    state: DesktopAppState,
    isFullscreen: Boolean,
    expanded: Boolean,
    onToggleFullscreen: () -> Unit,
    onFocusSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (expanded) 212.dp else 76.dp,
        animationSpec = tween(220),
        label = "rail-width",
    )
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(YomiraColors.OledRaised.copy(alpha = 0.86f))
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (expanded) {
            Text(
                text = "MENÜ",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.4.sp),
                color = YomiraColors.TextMuted.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        } else {
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .size(width = 18.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(BrandGradient),
            )
        }

        RailItem(
            icon = Icons.Filled.Home,
            label = DesktopCopy.NAV_BROWSE,
            expanded = expanded,
            active = state.feedSource !is FeedSource.Search,
            onClick = {
                if (state.feedSource is FeedSource.Search) state.clearSearch()
            },
        )
        RailItem(
            icon = Icons.Outlined.Search,
            label = DesktopCopy.NAV_SEARCH,
            expanded = expanded,
            active = state.feedSource is FeedSource.Search,
            onClick = onFocusSearch,
        )
        RailItem(
            icon = Icons.Outlined.FilterAlt,
            label = DesktopCopy.NAV_READABLE,
            expanded = expanded,
            active = state.readableOnly,
            onClick = state::toggleReadableOnly,
        )
        RailItem(
            icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            label = if (isFullscreen) DesktopCopy.EXIT_FULLSCREEN else DesktopCopy.FULLSCREEN,
            expanded = expanded,
            active = isFullscreen,
            onClick = onToggleFullscreen,
        )

        Spacer(Modifier.weight(1f))

        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline.copy(alpha = 0.7f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (expanded) 12.dp else 0.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(YomiraColors.Success))
            if (expanded) {
                Column {
                    Text(
                        DesktopCopy.VERSION,
                        style = MaterialTheme.typography.labelSmall,
                        color = YomiraColors.TextMuted,
                    )
                    Text(
                        "Windows",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = YomiraColors.TextMuted.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        targetValue = when {
            active -> YomiraColors.Purple.copy(alpha = 0.18f)
            hovered -> YomiraColors.SurfaceRaised
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "rail-fill",
    )
    val tint by animateColorAsState(
        targetValue = when {
            active -> YomiraColors.Lavender
            hovered -> YomiraColors.TextPrimary
            else -> YomiraColors.TextMuted
        },
        animationSpec = tween(150),
        label = "rail-tint",
    )
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (active) {
                    Modifier.border(1.dp, YomiraColors.Purple.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (expanded) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.spacedBy(11.dp) else Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        if (expanded) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) YomiraColors.TextPrimary else YomiraColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
