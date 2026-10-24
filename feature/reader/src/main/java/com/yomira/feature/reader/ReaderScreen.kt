package com.yomira.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.ImageLoader
import coil.memory.MemoryCache
import com.yomira.core.model.ReadingMode

/** Localized texts of the reader chrome (HUD, errors, mode menu). */
data class ReaderCopy(
    val retry: String = "Yeniden dene",
    val back: String = "Geri dön",
    val backShort: String = "Geri",
    val saveRetry: String = "Kaydetmeyi yeniden dene",
    val pageOf: String = "Sayfa %1\$d / %2\$d",
    val pageDescription: String = "Sayfa %1\$d",
    val pageLoadFailed: String = "Sayfa %1\$d yüklenemedi.",
    val modeWebtoon: String = "Webtoon",
    val modeManga: String = "Manga",
    val modeWestern: String = "Western",
    val oledBlack: String = "OLED siyah",
    val invertedColors: String = "Ters renkler",
)

fun ReadingMode.label(copy: ReaderCopy = ReaderCopy()): String = when (this) {
    ReadingMode.WEBTOON -> copy.modeWebtoon
    ReadingMode.MANGA_RTL -> copy.modeManga
    ReadingMode.WESTERN_LTR -> copy.modeWestern
}

@Composable
fun ReaderScreen(mangaId: String, chapterId: String, model: ReaderViewModel, onBack: () -> Unit, defaultMode: ReadingMode = ReadingMode.MANGA_RTL, copy: ReaderCopy = ReaderCopy()) {
    val observed by model.state.collectAsStateWithLifecycle()
    val state = if (observed.mangaId == mangaId && observed.chapterId == chapterId) observed
        else ReaderUiState(mangaId = mangaId, chapterId = chapterId, mode = defaultMode)
    val context = LocalContext.current.applicationContext
    // Coil owns bitmap lifetimes. Disposed lazy items release their painters; a bounded LRU
    // reuses decoded pages. Never recycle a bitmap still referenced by Compose's renderer.
    val loader = remember(context) {
        ImageLoader.Builder(context).memoryCache { MemoryCache.Builder(context).maxSizePercent(.20).build() }
            .diskCache(null).crossfade(false).build()
    }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    LaunchedEffect(mangaId, chapterId) { model.open(mangaId, chapterId, defaultMode) }
    DisposableEffect(model, mangaId, chapterId) { onDispose { model.close(mangaId, chapterId) } }
    var jump by remember(mangaId, chapterId) { mutableStateOf<Int?>(null) }
    Box(Modifier.fillMaxSize().background(if (state.oled) Color.Black else Color(0xFF24232A))) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> Column(Modifier.align(Alignment.Center).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error.orEmpty())
                Button(onClick = { model.open(mangaId, chapterId, defaultMode) }) { Text(copy.retry) }
                TextButton(onClick = onBack) { Text(copy.back) }
            }
            else -> key(state.chapterId, state.mode) {
                if (state.mode == ReadingMode.WEBTOON) WebtoonReader(state, loader, jump, { jump = null }, model::recordPage, model::toggleHud, copy)
                else PagerReader(state, loader, jump, { jump = null }, model::recordPage, model::toggleHud, copy)
            }
        }
        key(mangaId, chapterId) {
            if (state.hudVisible) ReaderOverlay(state, onBack, { jump = null; model.setMode(it) }, { jump = it }, model::toggleOled, model::toggleInvert, copy)
        }
        state.progressError?.let { message ->
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { Text(message); TextButton(onClick = model::retryProgress) { Text(copy.saveRetry) } }
            }
        }
    }
}
