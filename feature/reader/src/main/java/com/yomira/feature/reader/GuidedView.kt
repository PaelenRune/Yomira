package com.yomira.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.guided.PanelBoundingBox
import kotlin.math.min

/**
 * Panel-by-panel ("Guided View") presentation of one page.
 *
 * The page content keeps rendering exactly as in the normal pager; this wrapper
 * only moves the camera to the current panel and advances on tap. Panels arrive
 * in reading order from [GuidedViewDetector], so the wrapper needs no knowledge
 * of manga/comic rules beyond which side advances.
 */
@Composable
internal fun GuidedViewReader(
    panels: List<PanelBoundingBox>,
    imageWidth: Int,
    imageHeight: Int,
    index: Int,
    rtl: Boolean,
    onIndexChange: (Int) -> Unit,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (panels.isEmpty()) {
        Box(modifier) { content() }
        return
    }
    val current = index.coerceIn(0, panels.lastIndex)
    val panel = panels[current]
    var container by remember { mutableStateOf(IntSize.Zero) }

    val camera = remember(container, panel, imageWidth, imageHeight) {
        cameraFor(container, panel, imageWidth, imageHeight)
    }

    Box(
        modifier
            .onSizeChanged { container = it }
            .pointerInput(current, panels.size, rtl, container) {
                detectTapGestures { tap: Offset ->
                    val forwardSide = if (rtl) tap.x < container.width / 2f else tap.x > container.width / 2f
                    when {
                        forwardSide && current < panels.lastIndex -> onIndexChange(current + 1)
                        !forwardSide && current > 0 -> onIndexChange(current - 1)
                        forwardSide -> onNavigate(1)
                        else -> onNavigate(-1)
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = camera.scale
                scaleY = camera.scale
                translationX = camera.translation.x
                translationY = camera.translation.y
                transformOrigin = TransformOrigin(0f, 0f)
            },
        ) {
            content()
        }
    }
}

private data class PanelCamera(val scale: Float, val translation: Offset)

/**
 * Maps a normalized panel box to the "fit inside container" image rect, then
 * computes the scale and translation that centre that box on screen.
 */
private fun cameraFor(
    container: IntSize,
    panel: PanelBoundingBox,
    imageWidth: Int,
    imageHeight: Int,
): PanelCamera {
    val width = container.width.toFloat()
    val height = container.height.toFloat()
    if (width <= 0f || height <= 0f || imageWidth <= 0 || imageHeight <= 0) {
        return PanelCamera(1f, Offset.Zero)
    }
    val imageAspect = imageWidth.toFloat() / imageHeight
    val containerAspect = width / height
    val displayWidth: Float
    val displayHeight: Float
    if (imageAspect > containerAspect) {
        displayWidth = width
        displayHeight = width / imageAspect
    } else {
        displayHeight = height
        displayWidth = height * imageAspect
    }
    val offsetX = (width - displayWidth) / 2f
    val offsetY = (height - displayHeight) / 2f

    val left = offsetX + panel.left * displayWidth
    val top = offsetY + panel.top * displayHeight
    val right = offsetX + panel.right * displayWidth
    val bottom = offsetY + panel.bottom * displayHeight
    val panelWidth = (right - left).coerceAtLeast(1f)
    val panelHeight = (bottom - top).coerceAtLeast(1f)

    val scale = min(width / panelWidth, height / panelHeight).coerceIn(1f, 5f)
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    return PanelCamera(
        scale = scale,
        translation = Offset(
            x = width / 2f - centerX * scale,
            y = height / 2f - centerY * scale,
        ),
    )
}

/**
 * Turns a decoded page bitmap into a down-sampled luminance grid the pure
 * [GuidedViewDetector] can analyse. Results are memoised per page URL because
 * the same page is re-measured on every recomposition and revisit.
 */
internal object PanelAnalyzer {
    private const val MAX_SAMPLE_WIDTH = 240
    private const val MAX_CACHED_PAGES = 12

    private val cache = object : LinkedHashMap<String, List<PanelBoundingBox>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PanelBoundingBox>>?): Boolean =
            size > MAX_CACHED_PAGES
    }

    fun analyze(bitmap: Bitmap, mode: ReadingMode, cacheKey: String): List<PanelBoundingBox> {
        synchronized(cache) { cache[cacheKey]?.let { return it } }
        // Hardware bitmaps cannot be read with getPixels(); copy the rare case
        // into a readable software buffer before sampling.
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val sampleWidth = min(readable.width, MAX_SAMPLE_WIDTH).coerceAtLeast(1)
        val sampleHeight = (readable.height.toFloat() * sampleWidth / readable.width)
            .toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(readable, sampleWidth, sampleHeight, true)
        val pixels = IntArray(sampleWidth * sampleHeight)
        scaled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        if (scaled !== readable && scaled !== bitmap) scaled.recycle()
        if (readable !== bitmap) readable.recycle()
        val luminances = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            (0.299f * red + 0.587f * green + 0.114f * blue).toInt().coerceIn(0, 255)
        }
        val panels = GuidedViewDetector.detect(
            LuminanceGrid.fromLuminances(sampleWidth, sampleHeight, luminances),
            mode,
        )
        synchronized(cache) { cache[cacheKey] = panels }
        return panels
    }
}
