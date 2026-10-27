package com.yomira.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.YomiraFilterChip
import com.yomira.core.ui.YomiraFloatingPill
import com.yomira.core.ui.theme.DeepOledBlack
import com.yomira.core.ui.theme.NeonLavender
import com.yomira.core.ui.theme.NeonPurple
import com.yomira.core.ui.theme.SurfaceHighest
import com.yomira.core.ui.theme.TextMuted
import com.yomira.core.ui.theme.TextPrimary
import com.yomira.core.ui.theme.TextSecondary

/**
 * Sanya-Madre style reading chrome: everything floats. A slim capsule anchors
 * the series title at the top; a scrubber capsule and a scrollable control
 * capsule sit at the bottom. Nothing spans the page edge-to-edge, so the
 * artwork keeps the frame and the UI never becomes a second reading surface.
 *
 * [onChapter] is optional: hosts that cannot switch chapters still get the
 * scrubber, mode, night and recap controls.
 */
@Composable
fun ReaderOverlay(
    state: ReaderUiState,
    onBack: () -> Unit,
    onMode: (ReadingMode) -> Unit,
    onSeek: (Int) -> Unit,
    onOled: () -> Unit,
    onInvert: () -> Unit,
    onGuided: () -> Unit,
    onRecap: () -> Unit,
    copy: ReaderCopy = ReaderCopy(),
    onChapter: ((String) -> Unit)? = null,
) {
    var modeMenu by remember { mutableStateOf(false) }
    var chapterMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val total = state.pages.size
    val page = if (total == 0) 0 else (dragging?.toInt() ?: state.pageIndex).coerceIn(0, total - 1)
    val currentChapter = state.chapters.indexOfFirst { it.id == state.chapterId }
    val previous = state.chapters.getOrNull(currentChapter - 1)
    val next = state.chapters.getOrNull(currentChapter + 1)

    Box(Modifier.fillMaxSize()) {
        // Soft scrims keep the capsules readable over bright or white artwork
        // without dimming the page itself.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(128.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(DeepOledBlack.copy(alpha = .72f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, DeepOledBlack.copy(alpha = .86f)),
                    ),
                ),
        )

        YomiraFloatingPill(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            ReaderIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = copy.backShort,
                onClick = onBack,
            )
            Column(
                Modifier.weight(1f).padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    state.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
                Text(
                    state.chapterTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            if (next != null && onChapter != null) {
                TextButton(
                    onClick = { onChapter(next.id) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        copy.nextChapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonLavender,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Page scrubber: a single quiet line with a live counter.
            YomiraFloatingPill(Modifier.fillMaxWidth()) {
                Text(
                    String.format(copy.pageShort, if (total == 0) 0 else page + 1, total),
                    Modifier.padding(start = 8.dp, end = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                )
                Slider(
                    value = if (total <= 1) 0f else page.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeek(it.toInt()) }
                        dragging = null
                    },
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                    enabled = total > 1,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonLavender,
                        activeTrackColor = NeonPurple,
                        inactiveTrackColor = SurfaceHighest,
                    ),
                )
            }

            // Control capsule: mode, chapter picker, night, lighting, guided,
            // recap. It scrolls sideways instead of wrapping into a second row.
            YomiraFloatingPill(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        YomiraFilterChip(
                            selected = false,
                            onClick = { modeMenu = true },
                            label = state.mode.label(copy),
                            leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
                        )
                        DropdownMenu(
                            expanded = modeMenu,
                            onDismissRequest = { modeMenu = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = SurfaceHighest,
                        ) {
                            ReadingMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label(copy), style = MaterialTheme.typography.bodyMedium) },
                                    trailingIcon = {
                                        if (mode == state.mode) {
                                            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = NeonLavender)
                                        }
                                    },
                                    onClick = { modeMenu = false; onMode(mode) },
                                )
                            }
                        }
                    }
                    if (state.chapters.isNotEmpty() && onChapter != null) {
                        Box {
                            YomiraFilterChip(
                                selected = chapterMenu,
                                onClick = { chapterMenu = true },
                                label = copy.chapterPicker,
                                leadingIcon = Icons.Default.FormatListBulleted,
                            )
                            DropdownMenu(
                                expanded = chapterMenu,
                                onDismissRequest = { chapterMenu = false },
                                shape = RoundedCornerShape(18.dp),
                                containerColor = SurfaceHighest,
                                modifier = Modifier.heightIn(max = 380.dp),
                            ) {
                                LazyColumn(Modifier.widthIn(min = 220.dp, max = 300.dp)) {
                                    item {
                                        Text(
                                            copy.chapterPicker,
                                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextMuted,
                                        )
                                    }
                                    items(state.chapters, key = { it.id }) { chapter ->
                                        val selected = chapter.id == state.chapterId
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    chapter.title,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (selected) NeonLavender else TextSecondary,
                                                )
                                            },
                                            trailingIcon = {
                                                if (selected) {
                                                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = NeonLavender)
                                                }
                                            },
                                            onClick = {
                                                chapterMenu = false
                                                if (!selected) onChapter(chapter.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    YomiraFilterChip(
                        selected = state.oled,
                        onClick = onOled,
                        label = copy.oledBlack,
                        leadingIcon = Icons.Default.DarkMode,
                    )
                    YomiraFilterChip(
                        selected = state.inverted,
                        onClick = onInvert,
                        label = copy.invertedColors,
                        leadingIcon = Icons.Default.InvertColors,
                    )
                    // Guided View is off for webtoons: they are one continuous
                    // column, there are no panels to hop between.
                    if (state.mode != ReadingMode.WEBTOON) {
                        YomiraFilterChip(
                            selected = state.guidedView,
                            onClick = onGuided,
                            label = copy.guidedView,
                            leadingIcon = Icons.Default.PanTool,
                        )
                    }
                    if (state.recap != null) {
                        // Highlights when the reader notices a long break: the
                        // recap only ever covers chapters up to this one.
                        YomiraFilterChip(
                            selected = state.recapDue,
                            onClick = onRecap,
                            label = copy.recapOpen,
                            leadingIcon = Icons.Default.Refresh,
                        )
                    }
                }
            }
        }
    }
}

/** Compact icon button sized for the floating capsules. */
@Composable
private fun ReaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) TextPrimary else TextMuted.copy(alpha = .35f),
        )
    }
}
