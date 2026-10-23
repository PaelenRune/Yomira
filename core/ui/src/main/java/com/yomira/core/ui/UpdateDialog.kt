package com.yomira.core.ui

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
import com.yomira.core.ui.theme.DeepOledBlack
import com.yomira.core.ui.theme.NeonPurple
import com.yomira.core.ui.theme.TextMuted
import com.yomira.core.ui.theme.TextPrimary

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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = DeepOledBlack,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = NeonPurple,
                modifier = Modifier.size(36.dp),
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Yeni Sürüm Mevcut!",
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
                Text("Değişiklikler", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
                Text(
                    "APK, GitHub Releases sayfası üzerinden tarayıcıda indirilir.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = Color.White,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Şimdi Güncelle")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Daha sonra", color = TextMuted) }
        },
    )
}
