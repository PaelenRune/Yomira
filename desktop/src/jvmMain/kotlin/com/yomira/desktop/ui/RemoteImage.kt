package com.yomira.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage

/**
 * Bounded, process-local bitmap cache for remote artwork.
 *
 * Covers are references, not bundled media: the loader streams them over HTTPS
 * with the same OkHttp pool as the catalogue. A byte budget (rather than an
 * entry count) keeps memory predictable when 4K banners and thumbnail covers
 * share the cache. Nothing is written to disk.
 *
 * Two internal schemes are also understood, both of which never touch the
 * network: `asset://` resolves against [assetRoot] (the showcase artwork the
 * headless render suite composes) and is inert while the root is null. HTTPS
 * remains the only production source of catalogue artwork.
 */
object RemoteImages {
    private const val MAX_BYTES = 96L * 1024 * 1024

    /** Root directory that backs `asset://` paths; null outside showcase renders. */
    @Volatile
    var assetRoot: File? = null

    private data class Entry(val bitmap: ImageBitmap, val bytes: Long)

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = false
        },
    )
    private var cachedBytes = 0L

    suspend fun load(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ImageBitmap? {
        val supported = url.startsWith("https://") || url.startsWith(ASSET_SCHEME)
        if (!supported) return null
        cache[url]?.let { return it.bitmap }
        val bitmap = withContext(Dispatchers.IO) {
            if (url.startsWith(ASSET_SCHEME)) {
                readAsset(url.removePrefix(ASSET_SCHEME))?.let(::decode)
            } else {
                val request = Request.Builder()
                    .url(url)
                    .apply { headers.forEach { (key, value) -> header(key, value) } }
                    .get()
                    .build()
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val bytes = response.body?.bytes() ?: return@use null
                        decode(bytes)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        } ?: return null
        val weight = bitmap.width.toLong() * bitmap.height.toLong() * 4L
        synchronized(cache) {
            val previous = cache.put(url, Entry(bitmap, weight))
            cachedBytes += weight - (previous?.bytes ?: 0L)
            val iterator = cache.entries.iterator()
            while (cachedBytes > MAX_BYTES && iterator.hasNext()) {
                val eldest = iterator.next()
                cachedBytes -= eldest.value.bytes
                iterator.remove()
            }
        }
        return bitmap
    }

    private fun readAsset(path: String): ByteArray? {
        val root = assetRoot ?: return null
        // Reject traversal: only files that really sit inside the asset root are served.
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith(root.canonicalFile.path)) return null
        return runCatching { file.takeIf(File::isFile)?.readBytes() }.getOrNull()
    }

    private fun decode(bytes: ByteArray): ImageBitmap? =
        runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    /** Internal scheme of the bundled showcase artwork. */
    const val ASSET_SCHEME = "asset://"
}

private sealed interface ImageState {
    data object Loading : ImageState
    data object Missing : ImageState
    data class Loaded(val bitmap: ImageBitmap) : ImageState
}

/**
 * Cover/banner/reader-page renderer with a calm OLED placeholder. Loading and
 * decoding happen off the UI thread; the cache never retains a bitmap after the
 * byte budget is exceeded.
 */
@Composable
fun RemoteImage(
    url: String?,
    client: OkHttpClient,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    key: String? = null,
    fillParent: Boolean = false,
    headers: Map<String, String> = emptyMap(),
    onLoaded: (ImageBitmap) -> Unit = {},
) {
    val state by produceState<ImageState>(
        initialValue = if (url == null) ImageState.Missing else ImageState.Loading,
        key1 = key ?: url,
    ) {
        value = if (url == null) {
            ImageState.Missing
        } else {
            RemoteImages.load(client, url, headers)?.let { ImageState.Loaded(it) } ?: ImageState.Missing
        }
    }
    LaunchedEffect(state) {
        (state as? ImageState.Loaded)?.let { onLoaded(it.bitmap) }
    }
    Crossfade(
        targetState = state,
        modifier = modifier,
        animationSpec = tween(durationMillis = 220),
        label = "artwork",
    ) { current ->
        when (current) {
            is ImageState.Loaded -> Image(
                bitmap = current.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
            else -> Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(YomiraColors.Surface, YomiraColors.SurfaceRaised, YomiraColors.Surface),
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (current is ImageState.Missing) {
                    Icon(
                        imageVector = Icons.Outlined.ImageNotSupported,
                        contentDescription = null,
                        tint = YomiraColors.OutlineSoft.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
