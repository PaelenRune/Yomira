package com.yomira.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun ReaderScreen(mangaId: String, chapterId: String, model: ReaderViewModel, onBack: () -> Unit) {
    val observed by model.state.collectAsStateWithLifecycle()
    val state = if (observed.mangaId == mangaId && observed.chapterId == chapterId) observed
        else ReaderUiState(mangaId = mangaId, chapterId = chapterId)
    val context = LocalContext.current.applicationContext
    // Coil owns bitmap lifetimes. Disposed lazy items release their painters; a bounded LRU
    // reuses decoded pages. Never recycle a bitmap still referenced by Compose's renderer.
    val loader = remember(context) {
        ImageLoader.Builder(context).memoryCache { MemoryCache.Builder(context).maxSizePercent(.20).build() }
            .diskCache(null).crossfade(false).build()
    }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    LaunchedEffect(mangaId, chapterId) { model.open(mangaId, chapterId) }
    DisposableEffect(model, mangaId, chapterId) { onDispose { model.close(mangaId, chapterId) } }
    var jump by remember(mangaId, chapterId) { mutableStateOf<Int?>(null) }
    Box(Modifier.fillMaxSize().background(if (state.oled) Color.Black else Color(0xFF24232A))) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error.orEmpty())
                Button(onClick = { model.open(mangaId, chapterId) }) { Text("Yeniden dene") }
                TextButton(onClick = onBack) { Text("Geri dön") }
            }
            else -> key(state.chapterId, state.mode) {
                if (state.mode == ReadingMode.WEBTOON) WebtoonReader(state, loader, jump, { jump = null }, model::recordPage, model::toggleHud)
                else PagerReader(state, loader, jump, { jump = null }, model::recordPage, model::toggleHud)
            }
        }
        if (state.hudVisible) ReaderOverlay(state, onBack, { jump = null; model.setMode(it) }, { jump = it }, model::toggleOled, model::toggleInvert)
        state.progressError?.let { message ->
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(16.dp)) { Text(message); TextButton(onClick = model::retryProgress) { Text("Kaydetmeyi yeniden dene") } }
            }
        }
    }
}
