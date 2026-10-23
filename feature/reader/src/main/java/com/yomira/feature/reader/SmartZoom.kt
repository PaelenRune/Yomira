package com.yomira.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

internal fun clampPan(offset: Offset, scale: Float, size: IntSize): Offset {
    if (scale <= 1f) return Offset.Zero
    val x = size.width * (scale - 1f).coerceAtLeast(0f) / 2f
    val y = size.height * (scale - 1f).coerceAtLeast(0f) / 2f
    return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
}

/** Zooms around the touched point, rather than jumping to the center of the page. */
@Composable
fun SmartZoom(modifier: Modifier = Modifier, onTap: () -> Unit, onZoomed: (Boolean) -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tap by rememberUpdatedState(onTap)
    val zoomed by rememberUpdatedState(onZoomed)
    DisposableEffect(Unit) { onDispose { zoomed(false) } }
    Box(modifier.clipToBounds().onSizeChanged { size = it; offset = clampPan(offset, scale, it) }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { tap() }, onDoubleTap = { point ->
                val next = if (scale > 1.01f) 1f else 2.5f
                val center = Offset(size.width / 2f, size.height / 2f)
                offset = if (next == 1f) Offset.Zero else clampPan((center - point) * (next - 1f), next, size)
                scale = next
                zoomed(next > 1f)
            })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var totalPan = Offset.Zero
                do {
                    val event = awaitPointerEvent()
                    val factor = event.calculateZoom()
                    val pan = event.calculatePan()
                    totalPan += pan
                    val multiple = event.changes.count { it.pressed } > 1
                    val transforming = multiple || (scale > 1f && totalPan.getDistance() > viewConfiguration.touchSlop)
                    if (transforming) {
                        val next = (scale * factor).coerceIn(1f, 5f)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val focal = event.calculateCentroid(useCurrent = false)
                        offset = clampPan((offset + center - focal) * (next / scale) - center + focal + pan, next, size)
                        scale = next
                        zoomed(next > 1.01f)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
        Box(Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offset.x; translationY = offset.y
        }) { content() }
    }
}
