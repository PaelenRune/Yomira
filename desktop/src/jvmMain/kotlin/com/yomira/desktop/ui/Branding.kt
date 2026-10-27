package com.yomira.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomira.desktop.model.DesktopCopy
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

/**
 * Brand mark inside a violet halo. The halo is a drawn ring rather than a
 * shader blur so the masthead stays cheap in the 4K render test.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, glowing: Boolean = true) {
    val bitmap = appLogo
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier
            .clip(shape)
            .background(YomiraColors.SurfaceRaised)
            .border(
                1.dp,
                if (glowing) YomiraColors.Purple.copy(alpha = 0.55f) else YomiraColors.Outline,
                shape,
            )
            .padding(2.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(YomiraColors.Purple, YomiraColors.Lavender))),
            )
        }
    }
}

/** Brand lockup: mark, "YOMIRA" wordmark and the reader caption. */
@Composable
fun BrandLockup(compact: Boolean = false, showTagline: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLogo(Modifier.size(if (compact) 32.dp else 38.dp), glowing = !compact)
        if (!compact) {
            Column {
                Text(
                    text = DesktopCopy.APP_NAME.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp,
                    color = YomiraColors.TextPrimary,
                )
                Text(
                    text = if (showTagline) DesktopCopy.TAGLINE else DesktopCopy.CHANNEL,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (showTagline) YomiraColors.TextMuted else YomiraColors.Lavender.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
