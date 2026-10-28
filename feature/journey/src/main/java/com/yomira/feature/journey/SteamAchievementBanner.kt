package com.yomira.feature.journey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.domain.journey.AchievementUnlockEvent

/** Presentation only; the root host owns the timeout and serializes simultaneous unlocks. */
@Composable
fun YomiraAchievementBanner(
    event: AchievementUnlockEvent?,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    turkish: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible && event != null,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { -it * 2 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        ) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it * 2 }, animationSpec = tween(250)) + fadeOut(tween(200)),
    ) {
        if (event != null) {
            val title = if (turkish) event.titleTr else event.titleEn
            val description = if (turkish) event.descriptionTr else event.descriptionEn
            val gold = Color(0xFFFFD77A)
            Surface(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(maxWidth = 480.dp)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        dismiss { onDismiss(); true }
                    }
                    .pointerInput(event) {
                        val threshold = 24.dp.toPx()
                        var upwardDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { upwardDrag = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                upwardDrag -= amount
                                if (upwardDrag > threshold) onDismiss()
                            },
                        )
                    },
                color = Color(0xF5090C12),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Brush.linearGradient(listOf(gold, Color(0xFF62502B), gold))),
                shadowElevation = 16.dp,
            ) {
                Row(
                    Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(event.emoji.ifBlank { "🏆" }, fontSize = 32.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (turkish) "BAŞARIM KAZANILDI! (+${event.xpBonus} XP)"
                            else "ACHIEVEMENT UNLOCKED! (+${event.xpBonus} XP)",
                            color = gold,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$title — ${if (turkish) event.tier.labelTr else event.tier.labelEn}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(description, color = Color(0xFFC6CAD4), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
