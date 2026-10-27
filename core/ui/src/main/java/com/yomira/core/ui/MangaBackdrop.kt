package com.yomira.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.yomira.core.ui.theme.DeepOledBlack

/** Sharp landscape artwork; a missing, loading or failed banner uses the poster mirror.
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
            CleanPosterReflection(coverUrl, Modifier.matchParentSize(), background)
        } else {
            SubcomposeAsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                loading = { CleanPosterReflection(coverUrl, Modifier.matchParentSize(), background) },
                error = { CleanPosterReflection(coverUrl, Modifier.matchParentSize(), background) },
            )
        }
    }
}

/** Vertically inverted crisp poster with a linear fade directly into the OLED canvas. */
@Composable
private fun CleanPosterReflection(coverUrl: String?, modifier: Modifier, background: Color) {
    Box(modifier.background(background)) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().graphicsLayer { scaleY = -1f },
        )
        Box(Modifier.matchParentSize().background(
            Brush.verticalGradient(listOf(background.copy(alpha = .12f), background)),
        ))
    }
}
