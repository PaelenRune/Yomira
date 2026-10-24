package com.yomira.feature.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.model.ReadingMode

@Composable
fun ReaderOverlay(state: ReaderUiState, onBack: () -> Unit, onMode: (ReadingMode) -> Unit,
    onSeek: (Int) -> Unit, onOled: () -> Unit, onInvert: () -> Unit, copy: ReaderCopy = ReaderCopy()) {
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, copy.backShort) }
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(state.chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text(state.mode.label(copy)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ReadingMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label(copy)) }, onClick = { menu = false; onMode(mode) }) }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(String.format(copy.pageOf, if (state.pages.isEmpty()) 0 else (dragging?.toInt() ?: state.pageIndex) + 1, state.pages.size), style = MaterialTheme.typography.labelLarge)
                Slider(value = dragging ?: state.pageIndex.toFloat(), onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { onSeek(it.toInt()) }; dragging = null },
                    valueRange = 0f..(state.pages.size - 1).coerceAtLeast(1).toFloat(), enabled = state.pages.size > 1)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = state.oled, onClick = onOled, label = { Text(copy.oledBlack) })
                    FilterChip(selected = state.inverted, onClick = onInvert, label = { Text(copy.invertedColors) })
                }
            }
        }
    }
}
