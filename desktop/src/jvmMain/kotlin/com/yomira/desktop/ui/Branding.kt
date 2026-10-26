package com.yomira.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage

/**
 * The packaged brand mark, decoded once from the module classpath. Compose's
 * `painterResource(String)` desktop overload is deprecated, so the bitmap is
 * loaded explicitly and drawn through the same Image pipeline as remote art.
 */
private val appLogo: ImageBitmap? by lazy {
    runCatching {
        val stream = object {}.javaClass.getResourceAsStream("/yomira.png") ?: return@runCatching null
        SkiaImage.makeFromEncoded(stream.use { it.readBytes() }).toComposeImageBitmap()
    }.getOrNull()
}

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    val bitmap = appLogo
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(9.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier.clip(RoundedCornerShape(9.dp)).background(YomiraColors.Purple.copy(alpha = 0.35f)))
    }
}
