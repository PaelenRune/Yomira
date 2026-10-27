package com.yomira.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yomira.core.ui.theme.CardSurface
import com.yomira.core.ui.theme.HairlineBorder
import com.yomira.core.ui.theme.NeonLavender
import com.yomira.core.ui.theme.NeonPurple
import com.yomira.core.ui.theme.TextMuted
import com.yomira.core.ui.theme.TextPrimary
import com.yomira.core.ui.theme.TextSecondary

/**
 * OLED update notice. Purely presentational: the caller owns the download
 * intent so the UI layer never decides where an APK comes from.
 */
@Composable
fun UpdateDialog(
    version: String,
    releaseNotes: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    copy: UpdateDialogCopy = UpdateDialogCopy(),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.border(1.dp, HairlineBorder, RoundedCornerShape(24.dp)),
        containerColor = CardSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NeonPurple.copy(alpha = .12f),
                border = BorderStroke(1.dp, NeonPurple.copy(alpha = .25f)),
                tonalElevation = 0.dp,
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = NeonLavender,
                    modifier = Modifier.padding(14.dp).size(32.dp),
                )
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    copy.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "($version)",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonPurple,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(copy.changesLabel, style = MaterialTheme.typography.labelLarge, color = TextMuted)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Text(
                    copy.apkNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = Color.White,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(copy.updateNow)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(copy.later, color = TextMuted) }
        },
    )
}
