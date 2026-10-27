package com.yomira.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.yomira.core.ui.theme.DeepOledBlack

/** Sharp landscape artwork; a missing, loading or failed banner uses an upright poster.
 * No render effects, bitmap transformations, animation, or offscreen alpha masks.
 */
@Composable
fun MangaBackdrop(
    bannerUrl: String?,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    background: Color = DeepOledBlack,
) {
    Box(modifier.clipToBounds().background(background)) {
        val banner = bannerUrl?.trim()?.takeIf(String::isNotEmpty)
        if (banner == null) {
            PosterBackdrop(coverUrl, Modifier.matchParentSize(), background)
        } else {
            SubcomposeAsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { PosterBackdrop(coverUrl, Modifier.matchParentSize(), background) },
                error = { PosterBackdrop(coverUrl, Modifier.matchParentSize(), background) },
            )
        }
    }
}

/** Upright poster with a dark scrim fading directly into the OLED canvas. */
@Composable
private fun PosterBackdrop(coverUrl: String?, modifier: Modifier, background: Color) {
    Box(modifier.background(background)) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().background(
            Brush.verticalGradient(
                0f to background.copy(alpha = .20f),
                .40f to background.copy(alpha = .48f),
                .75f to background.copy(alpha = .85f),
                1f to background,
            ),
        ))
    }
}
