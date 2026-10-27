package com.yomira.feature.reader

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.transform.Transformation
import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.guided.PanelBoundingBox
import com.yomira.domain.extension.SourcePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val invertMatrix = ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
))

@Composable
internal fun ReaderPage(
    page: SourcePage,
    imageLoader: ImageLoader,
    inverted: Boolean,
    webtoon: Boolean,
    onTap: () -> Unit,
    onZoomed: (Boolean) -> Unit,
    copy: ReaderCopy = ReaderCopy(),
    mode: ReadingMode = ReadingMode.MANGA_RTL,
    guided: Boolean = false,
    onNavigate: (Int) -> Unit = {},
    seamTrim: Transformation? = null,
    decodeMaxHeight: Int = 4096,
    onGeometry: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var aspect by remember(page.imageUrl) { mutableFloatStateOf(.7f) }
    var retry by remember(page.imageUrl) { mutableIntStateOf(0) }
    var panelIndex by remember(page.imageUrl) { mutableIntStateOf(0) }
    var panels by remember(page.imageUrl, mode) { mutableStateOf(emptyList<PanelBoundingBox>()) }
    var decoded by remember(page.imageUrl, retry) { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Panel detection is deterministic per page and runs off the UI thread;
    // the memoised result is reused on every recomposition and revisit.
    LaunchedEffect(decoded, mode, guided) {
        val bitmap = decoded ?: return@LaunchedEffect
        if (!guided) {
            panels = emptyList()
            return@LaunchedEffect
        }
        panels = withContext(Dispatchers.Default) {
            PanelAnalyzer.analyze(bitmap, mode, cacheKey = page.imageUrl)
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().then(if (webtoon) Modifier else Modifier.fillMaxHeight()), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth.coerceAtLeast(1)
        val request = remember(page, width, retry, decodeMaxHeight, seamTrim, guided) {
            ImageRequest.Builder(context).data(page.imageUrl)
                // Pixel analysis/transforms need software; ordinary pages stay hardware-backed.
                .allowHardware(!guided && seamTrim == null)
                .bitmapConfig(if (guided || seamTrim != null) android.graphics.Bitmap.Config.ARGB_8888 else android.graphics.Bitmap.Config.HARDWARE)
                .setParameter("reader-retry", retry)
                .size(width, decodeMaxHeight).precision(Precision.INEXACT)
                .apply {
                    page.headers.forEach { (key, value) -> addHeader(key, value) }
                    if (seamTrim != null) transformations(seamTrim)
                }
                .crossfade(false).build()
        }
        val frame = if (webtoon) Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.fillMaxSize()
        val cameraPanels = panels
        val content = @Composable {
            SubcomposeAsyncImage(
                model = request, imageLoader = imageLoader,
                contentDescription = String.format(copy.pageDescription, page.index + 1),
                modifier = Modifier.fillMaxSize(), contentScale = if (webtoon) ContentScale.FillWidth else ContentScale.Fit,
                colorFilter = if (inverted) ColorFilter.colorMatrix(invertMatrix) else null,
                onSuccess = { success ->
                    val drawable = success.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        aspect = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                        onGeometry(drawable.intrinsicWidth, drawable.intrinsicHeight)
                        decoded = (drawable as? BitmapDrawable)?.bitmap
                    }
                },
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) } },
                error = { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(String.format(copy.pageLoadFailed, page.index + 1))
                    TextButton(onClick = { retry++ }) { Text(copy.retry) }
                } },
            )
        }
        if (guided && cameraPanels.isNotEmpty() && decoded != null) {
            val bitmap = decoded
            if (bitmap != null) {
                GuidedViewReader(
                    panels = cameraPanels,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    index = panelIndex,
                    rtl = mode.isRightToLeft(),
                    onIndexChange = { panelIndex = it },
                    onNavigate = onNavigate,
                    modifier = frame,
                ) { content() }
            }
        } else {
            SmartZoom(frame, onTap, onZoomed) { content() }
        }
    }
}
