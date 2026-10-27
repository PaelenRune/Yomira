package com.yomira.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.FeedSource

/**
 * Modern desktop masthead, adapted from the Cstation comic-reader web layout:
 * brand lockup on the left, a quiet `Ctrl+F` search capsule in the centre and
 * the release badge, fullscreen and settings controls on the right.
 *
 * The masthead is a single 78dp band; the page HUD stays in the footer so the
 * top of the window never competes with the artwork.
 */
@Composable
fun TopBar(
    state: DesktopAppState,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    searchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) YomiraColors.Purple else YomiraColors.OutlineSoft,
        animationSpec = tween(180),
        label = "search-border",
    )
    val panelOffset = with(LocalDensity.current) {
        IntOffset(-ShellPadding.roundToPx(), 86.dp.roundToPx())
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val roomy = maxWidth >= 1_080.dp
        val veryRoomy = maxWidth >= 1_460.dp
        val narrow = maxWidth < 720.dp

        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(YomiraColors.OledRaised, YomiraColors.OledRaised.copy(alpha = 0.90f)),
                        ),
                    )
                    .padding(horizontal = if (narrow) 14.dp else ShellPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 20.dp),
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusManager.clearFocus()
                            state.clearSearch()
                        }
                        .padding(end = 6.dp),
                ) {
                    BrandLockup(compact = !roomy, showTagline = veryRoomy)
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SearchField(
                        state = state,
                        focusRequester = searchFocusRequester,
                        borderColor = borderColor,
                        onFocusChanged = {
                            focused = it
                            state.isSearchFocused = it
                        },
                        onSubmit = {
                            state.submitSearch()
                            focusManager.clearFocus()
                        },
                        compact = !roomy,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 540.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (roomy) VersionBadge()
                    if (state.feedSource is FeedSource.Search && veryRoomy) {
                        GhostButton(
                            text = "Keşfet'e dön",
                            onClick = {
                                focusManager.clearFocus()
                                state.clearSearch()
                            },
                        )
                    }
                    IconBubble(
                        icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) DesktopCopy.EXIT_FULLSCREEN else DesktopCopy.FULLSCREEN,
                        onClick = onToggleFullscreen,
                        size = 40.dp,
                        tint = if (isFullscreen) YomiraColors.Lavender else YomiraColors.TextPrimary,
                    )
                    IconBubble(
                        icon = Icons.Outlined.Settings,
                        contentDescription = DesktopCopy.SETTINGS,
                        onClick = { settingsOpen = !settingsOpen },
                        size = 40.dp,
                        tint = if (settingsOpen) YomiraColors.Lavender else YomiraColors.TextPrimary,
                        background = if (settingsOpen) {
                            YomiraColors.Purple.copy(alpha = 0.18f)
                        } else {
                            YomiraColors.SurfaceRaised.copy(alpha = 0.6f)
                        },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline.copy(alpha = 0.9f)))
        }

        if (settingsOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = panelOffset,
                onDismissRequest = { settingsOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                SettingsPanel(
                    state = state,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onClose = { settingsOpen = false },
                )
            }
        }
    }
}

@Composable
private fun VersionBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(YomiraColors.Surface.copy(alpha = 0.9f))
            .border(1.dp, YomiraColors.OutlineSoft, RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(YomiraColors.Success))
        Text(
            text = DesktopCopy.VERSION,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = YomiraColors.TextMuted,
        )
    }
}

@Composable
private fun SearchField(
    state: DesktopAppState,
    focusRequester: FocusRequester,
    borderColor: Color,
    onFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        targetValue = if (hovered) YomiraColors.SurfaceRaised else YomiraColors.Surface.copy(alpha = 0.92f),
        animationSpec = tween(180),
        label = "search-fill",
    )
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = state.query,
        onValueChange = state::updateQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = YomiraColors.TextPrimary),
        cursorBrush = SolidColor(YomiraColors.Lavender),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        interactionSource = interaction,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .onPreviewKeyEvent { event ->
                when {
                    // Enter submits even when the IME action is not routed by the host.
                    event.key == Key.Enter -> {
                        onSubmit()
                        true
                    }
                    // BLUR_SEARCH from the global resolver is not consumed there;
                    // the focused field clears its own focus here.
                    event.key == Key.Escape -> {
                        focusManager.clearFocus()
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(48.dp)
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, borderColor, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (borderColor == YomiraColors.Purple) YomiraColors.Lavender else YomiraColors.TextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.query.isEmpty()) {
                        Text(
                            text = if (compact) DesktopCopy.SEARCH_SHORT_HINT else DesktopCopy.SEARCH_HINT,
                            style = MaterialTheme.typography.bodyLarge,
                            color = YomiraColors.TextMuted.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (state.query.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Aramayı temizle",
                        tint = YomiraColors.TextMuted,
                        modifier = Modifier
                            .size(17.dp)
                            .clickable {
                                state.updateQuery("")
                                state.clearSearch()
                            },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    KeyCap("Ctrl")
                    KeyCap("F", accent = true)
                }
            }
        },
    )
}

/**
 * Settings drop-down. Everything it offers is wired to real shell behaviour:
 * the catalogue filter, fullscreen and the keyboard map. No decorative toggles.
 */
@Composable
internal fun SettingsPanel(
    state: DesktopAppState,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .widthIn(min = 288.dp, max = 320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(YomiraColors.OledRaised)
            .border(1.dp, YomiraColors.OutlineSoft, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                DesktopCopy.SETTINGS.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
                color = YomiraColors.Lavender,
            )
            Box(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Close,
                contentDescription = DesktopCopy.CLOSE,
                tint = YomiraColors.TextMuted,
                modifier = Modifier.size(16.dp).clickable(onClick = onClose),
            )
        }

        ToggleRow(
            title = "Yalnızca okunabilir",
            subtitle = "MangaDex kataloğu · uygulama içi okuyucu",
            checked = state.readableOnly,
            onToggle = state::toggleReadableOnly,
        )
        ToggleRow(
            title = "Tam ekran",
            subtitle = "F11 ile de değiştirilebilir",
            checked = isFullscreen,
            onToggle = onToggleFullscreen,
        )

        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        Text(
            "KLAVYE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
            color = YomiraColors.TextMuted,
        )
        ShortcutRow("Arama", listOf("Ctrl", "F"))
        ShortcutRow("Tam ekran", listOf("F11"))
        ShortcutRow("Katalog sayfası", listOf("←", "→"))
        ShortcutRow("Geri", listOf("Esc"))

        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${DesktopCopy.VERSION} · Windows",
                style = MaterialTheme.typography.labelSmall,
                color = YomiraColors.TextMuted,
            )
            Box(Modifier.weight(1f))
            Text(
                "Compose Desktop",
                style = MaterialTheme.typography.labelSmall,
                color = YomiraColors.TextMuted.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = YomiraColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = YomiraColors.TextMuted)
        }
        Box(
            Modifier
                .size(width = 40.dp, height = 22.dp)
                .clip(CircleShape)
                .background(if (checked) YomiraColors.Purple else YomiraColors.SurfaceHighest)
                .border(
                    1.dp,
                    if (checked) YomiraColors.Lavender.copy(alpha = 0.6f) else YomiraColors.OutlineSoft,
                    CircleShape,
                ),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else YomiraColors.TextMuted),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = YomiraColors.Purple,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(label: String, keys: List<String>) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = YomiraColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        keys.forEach { KeyCap(it) }
    }
}
