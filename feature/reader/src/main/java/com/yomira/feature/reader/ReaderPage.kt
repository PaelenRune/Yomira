package com.yomira.feature.reader

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
import com.yomira.domain.extension.SourcePage

private val invertMatrix = ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
))

@Composable
internal fun ReaderPage(page: SourcePage, imageLoader: ImageLoader, inverted: Boolean, webtoon: Boolean,
    onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    val context = LocalContext.current
    var aspect by remember(page.imageUrl) { mutableFloatStateOf(.7f) }
    var retry by remember(page.imageUrl) { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (webtoon) Modifier else Modifier.fillMaxHeight()), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth.coerceAtLeast(1)
        val request = remember(page, width, retry) {
            ImageRequest.Builder(context).data(page.imageUrl)
                .setParameter("reader-retry", retry)
                .size(width, 4096).precision(Precision.INEXACT)
                .apply { page.headers.forEach { (key, value) -> addHeader(key, value) } }
                .crossfade(false).build()
        }
        val frame = if (webtoon) Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.fillMaxSize()
        SmartZoom(frame, onTap, onZoomed) {
            SubcomposeAsyncImage(
                model = request, imageLoader = imageLoader,
                contentDescription = "Sayfa ${page.index + 1}",
                modifier = Modifier.fillMaxSize(), contentScale = if (webtoon) ContentScale.FillWidth else ContentScale.Fit,
                colorFilter = if (inverted) ColorFilter.colorMatrix(invertMatrix) else null,
                onSuccess = { success ->
                    val drawable = success.result.drawable
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        aspect = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                    }
                },
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) } },
                error = { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Sayfa ${page.index + 1} yüklenemedi.")
                    TextButton(onClick = { retry++ }) { Text("Yeniden dene") }
                } },
            )
        }
    }
}
