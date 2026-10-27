package com.yomira.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.yomira.desktop.data.CatalogPage
import com.yomira.desktop.data.CatalogRepository
import com.yomira.desktop.data.ChapterRepository
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopPage
import com.yomira.desktop.state.DesktopAppState
import com.yomira.desktop.state.PageCursor
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * README screenshot suite.
 *
 * Composes the shipping desktop shell at the documented form factors and writes
 * the frames straight into `art/` so the repository gallery is always produced
 * by the real UI rather than by a drawing. Artwork is decoded through the same
 * `RemoteImage` pipeline used in production; only the source differs (the
 * repository's own showcase fixtures instead of HTTPS providers).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopShowcaseScreensTest {

    private class ShowcaseCatalogRepository : CatalogRepository {
        override suspend fun browse(cursor: PageCursor, pageSize: Int): CatalogPage =
            CatalogPage(ShowcaseCatalog.items, hasNext = true)

        override suspend fun search(query: String): List<CatalogItem> = ShowcaseCatalog.items

        override suspend fun detail(item: CatalogItem) = ShowcaseCatalog.manga(item)
    }

    private class ShowcaseChapterRepository : ChapterRepository {
        override suspend fun chapters(item: CatalogItem): List<DesktopChapter> =
            ShowcaseCatalog.chapters(item)

        override suspend fun pages(chapter: DesktopChapter): List<DesktopPage> =
            ShowcaseCatalog.pages(chapter)
    }

    private fun state(scope: kotlinx.coroutines.CoroutineScope) =
        DesktopAppState(ShowcaseCatalogRepository(), ShowcaseChapterRepository(), scope)

    private fun render(
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ): ByteArray {
        System.setProperty("skiko.renderApi", "SOFTWARE")
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            YomiraDesktopTheme { content() }
        }
        return try {
            var bytes: ByteArray? = null
            // Artwork resolves through an async `produceState`; the crossfade then
            // needs a few frames. Render an animation clock forward until the
            // frame settles, then encode the final bitmap.
            repeat(8) { frame ->
                Thread.sleep(90)
                val image = scene.render(frame * 120_000_000L)
                assertEquals(width, image.width)
                assertEquals(height, image.height)
                bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
            }
            bytes ?: error("PNG encoding failed")
        } finally {
            scene.close()
        }
    }

    private fun shell(state: DesktopAppState, fullscreen: Boolean = false): @Composable () -> Unit = {
        YomiraDesktopApp(
            state = state,
            client = OkHttpClient(),
            isFullscreen = fullscreen,
            onToggleFullscreen = {},
            searchFocusRequester = androidx.compose.ui.focus.FocusRequester(),
        )
    }

    private fun saveArtwork(name: String, bytes: ByteArray) {
        val directory = File(ShowcaseAssets.repoRoot, "art").apply { mkdirs() }
        val file = File(directory, name)
        file.writeBytes(bytes)
        assertTrue("render must not be an empty frame", bytes.size > 100_000)
    }

    @Test
    fun `browse wall renders into the README gallery`() = runTest {
        val state = state(this)
        state.start()
        testScheduler.advanceUntilIdle()

        saveArtwork("screenshot_desktop_browse.png", render(2560, 1440, shell(state)))
    }

    @Test
    fun `detail dossier renders into the README gallery`() = runTest {
        val state = state(this)
        state.start()
        state.openDetail(ShowcaseCatalog.detail)
        testScheduler.advanceUntilIdle()

        saveArtwork("screenshot_desktop_detail.png", render(2560, 1440, shell(state)))
    }

    @Test
    fun `webtoon reader renders into the README gallery`() = runTest {
        val state = state(this)
        state.start()
        state.openDetail(ShowcaseCatalog.detail)
        testScheduler.advanceUntilIdle()
        state.openChapter(state.chapterList.first())
        testScheduler.advanceUntilIdle()

        saveArtwork("screenshot_desktop_reader.png", render(2560, 1440, shell(state)))
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun installArtworkAndWarmCache() {
            ShowcaseAssets.install()
            // Decode every fixture once so the composed frames start from a warm
            // cache; this keeps the suite deterministic instead of racing the
            // image producer of each poster.
            val urls = buildList {
                ShowcaseCatalog.items.forEach { item ->
                    item.coverUrl?.let(::add)
                    item.bannerUrl?.let(::add)
                }
                (1..4).forEach { add(ShowcaseAssets.page("solo-leveling", it)) }
            }
            runBlocking {
                val client = OkHttpClient()
                urls.forEach { url ->
                    val bitmap = RemoteImages.load(client, url)
                    check(bitmap != null) { "Showcase artwork did not decode: $url" }
                }
            }
        }
    }
}
