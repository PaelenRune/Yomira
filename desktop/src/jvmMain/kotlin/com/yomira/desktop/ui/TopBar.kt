package com.yomira.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.desktop.model.DesktopCopy
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.FeedSource

/**
 * Editorial desktop masthead: brand mark, inline search and the fullscreen
 * control. The page HUD lives in the footer so the top stays quiet.
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
    val borderColor by animateColorAsState(
        targetValue = if (focused) YomiraColors.Purple.copy(alpha = 0.9f) else YomiraColors.Outline,
        label = "search-border",
    )

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ShellPadding, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.clickable {
                    focusManager.clearFocus()
                    state.clearSearch()
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppLogo(Modifier.size(32.dp))
                Column {
                    Text(
                        text = DesktopCopy.APP_NAME.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp,
                        color = YomiraColors.TextPrimary,
                    )
                    Text(
                        text = DesktopCopy.TAGLINE,
                        style = MaterialTheme.typography.labelSmall,
                        color = YomiraColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.feedSource is FeedSource.Search) {
                    GhostButton(
                        text = "KeÅŸfet'e dÃ¶n",
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
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}

@Composable
private fun SearchField(
    state: DesktopAppState,
    focusRequester: FocusRequester,
    borderColor: Color,
    onFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = state.query,
        onValueChange = state::updateQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = YomiraColors.TextPrimary),
        cursorBrush = SolidColor(YomiraColors.Purple),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
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
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(YomiraColors.Surface.copy(alpha = 0.85f))
                    .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = YomiraColors.TextMuted,
                    modifier = Modifier.size(17.dp),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.query.isEmpty()) {
                        Text(
                            DesktopCopy.SEARCH_HINT,
                            style = MaterialTheme.typography.bodyLarge,
                            color = YomiraColors.TextMuted.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (state.query.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "AramayÄ± temizle",
                        tint = YomiraColors.TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                state.updateQuery("")
                                state.clearSearch()
                            },
                    )
                }
                MetaChip(text = "Ctrl F", accent = YomiraColors.TextMuted)
            }
        },
    )
}

