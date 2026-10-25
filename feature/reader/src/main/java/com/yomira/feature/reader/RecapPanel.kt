package com.yomira.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yomira.domain.journey.recap.SpoilerFreeRecap

/**
 * "Şimdiye kadar" sheet. It only ever renders the recap the engine produced,
 * which is already capped at the chapter before the one on screen; the UI adds
 * no data of its own, so it cannot leak a future chapter.
 */
@Composable
internal fun RecapPanel(
    recap: SpoilerFreeRecap?,
    onDismiss: () -> Unit,
    copy: ReaderCopy,
) {
    if (recap == null) return
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.padding(24.dp).fillMaxWidth(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(copy.recapTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                recap.resumeChapter?.let { chapter ->
                    Text(
                        String.format(copy.recapResume, chapterLabel(chapter)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                recap.premise?.let { premise ->
                    Text(premise, style = MaterialTheme.typography.bodyMedium)
                }
                if (recap.storyBeats.isEmpty()) {
                    Text(copy.recapEmpty, style = MaterialTheme.typography.bodyMedium)
                } else {
                    recap.storyBeats.forEach { beat ->
                        Text("•  $beat", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (recap.characters.isNotEmpty()) {
                    Text(
                        copy.recapCharacters,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(recap.characters.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                }
                if (recap.withheldChapters > 0) {
                    Text(
                        copy.recapWithheld,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text(copy.recapClose) }
            }
        }
    }
}

private fun chapterLabel(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
