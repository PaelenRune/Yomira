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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
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
    val pageShort: String = "%1\$d / %2\$d",
    val pageDescription: String = "Sayfa %1\$d",
    val pageLoadFailed: String = "Sayfa %1\$d yüklenemedi.",
    val modeWebtoon: String = "Webtoon",
    val modeManga: String = "Manga",
    val modeWestern: String = "Western",
    val oledBlack: String = "OLED siyah",
    val invertedColors: String = "Ters renkler",
    val guidedView: String = "Panel takibi",
    val chapterPicker: String = "Bölüm seç",
    val nextChapter: String = "Sonraki bölüm",
    val recapOpen: String = "Geçmiş olayları hatırla",
    val recapTitle: String = "Şimdiye kadar",
    val recapCharacters: String = "Karakterler",
    val recapResume: String = "Kaldığın yer: %1\$s",
    val recapWithheld: String = "Sonraki bölümler spoiler olmasın diye gizlendi.",
    val recapEmpty: String = "Henüz hatırlanacak bir olay yok.",
    val recapClose: String = "Kapat",
)

fun ReadingMode.label(copy: ReaderCopy = ReaderCopy()): String = when (this) {
    ReadingMode.WEBTOON -> copy.modeWebtoon
    ReadingMode.MANGA_RTL -> copy.modeManga
    ReadingMode.WESTERN_LTR -> copy.modeWestern
}

@Composable
fun ReaderScreen(mangaId: String, chapterId: String, model: ReaderViewModel, onBack: () -> Unit, defaultMode: ReadingMode = ReadingMode.MANGA_RTL,
    defaultGuidedView: Boolean = false, copy: ReaderCopy = ReaderCopy(), onChapter: ((String) -> Unit)? = null) {
    val observed by model.state.collectAsStateWithLifecycle()
    val state = if (observed.mangaId == mangaId && observed.chapterId == chapterId) observed
        else ReaderUiState(mangaId = mangaId, chapterId = chapterId, mode = defaultMode, guidedView = defaultGuidedView)
    val context = LocalContext.current.applicationContext
    // Coil owns bitmap lifetimes. Disposed lazy items release their painters; a bounded LRU
    // reuses decoded pages. Never recycle a bitmap still referenced by Compose's renderer.
    val loader = remember(context) {
        ImageLoader.Builder(context).memoryCache { MemoryCache.Builder(context).maxSizePercent(.20).build() }
            .diskCache(null).crossfade(false).build()
    }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    LaunchedEffect(mangaId, chapterId) { model.open(mangaId, chapterId, defaultMode, defaultGuidedView) }
    // Backgrounding must not count as reading time. The journey clock also stops
    // on its own after 60 seconds without a page interaction.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.pauseTracking() }
    DisposableEffect(model, mangaId, chapterId) { onDispose { model.close(mangaId, chapterId) } }
    var jump by remember(mangaId, chapterId) { mutableStateOf<Int?>(null) }

    // A wide stage (landscape phone or tablet) is the only place two pages are
    // shown at once; there the split spreads are stitched into single scenes.
    val configuration = LocalConfiguration.current
    val wideStage = configuration.screenWidthDp > configuration.screenHeightDp || configuration.screenWidthDp >= 600
    val geometry = remember(mangaId, chapterId) { mutableStateMapOf<Int, PageGeometry>() }
    val slots = if (!wideStage || state.mode == ReadingMode.WEBTOON) {
        state.pages.indices.map { SpreadSlot.Single(it) }
    } else {
        SpreadStitcher.plan(state.pages.map { page -> geometry[page.index] }, state.mode)
    }
    val slotKey = slots.joinToString(separator = ",") { it.firstPage.toString() }

    Box(Modifier.fillMaxSize().background(if (state.oled) Color.Black else Color(0xFF1B1A21))) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> Column(Modifier.align(Alignment.Center).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error.orEmpty())
                Button(onClick = { model.open(mangaId, chapterId, defaultMode, defaultGuidedView) }) { Text(copy.retry) }
                TextButton(onClick = onBack) { Text(copy.back) }
            }
            else -> key(state.chapterId, state.mode, slotKey) {
                if (state.mode == ReadingMode.WEBTOON) WebtoonReader(state, loader, jump, { jump = null }, model::recordPage, model::toggleHud, copy)
                else PagerReader(state, loader, jump, { jump = null }, model::recordPage, model::toggleHud, copy,
                    slots = slots, guided = state.guidedView,
                    onPageGeometry = { index, width, height -> geometry[index] = PageGeometry(index, width, height) })
            }
        }
        key(mangaId, chapterId) {
            if (state.hudVisible) ReaderOverlay(state, onBack, { jump = null; model.setMode(it) }, { jump = it },
                model::toggleOled, model::toggleInvert, model::toggleGuidedView, model::showRecap, copy, onChapter)
        }
        state.progressError?.let { message ->
            Surface(Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { Text(message); TextButton(onClick = model::retryProgress) { Text(copy.saveRetry) } }
            }
        }
        if (state.recapVisible) {
            RecapPanel(recap = state.recap, onDismiss = model::dismissRecap, copy = copy)
        }
    }
}