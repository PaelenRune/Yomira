package com.yomira.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.desktop.data.CatalogPage
import com.yomira.desktop.data.CatalogRepository
import com.yomira.desktop.data.ChapterRepository
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopPage
import com.yomira.desktop.state.DesktopAppState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless render smoke test.
 *
 * It composes the real shell at a 2K and a 4K canvas and writes the frames to
 * `desktop/build/reports/ui/` for visual review. The assertions catch the
 * failures that unit tests cannot: a Canvas size mismatch, a broken typography
 * resource, a modifier crash in the grid, or a Compose runtime error while the
 * detail/reader layers are mounted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopRenderSmokeTest {

    private class FakeCatalog : CatalogRepository {
        override suspend fun browse(cursor: com.yomira.desktop.state.PageCursor, pageSize: Int): CatalogPage =
            CatalogPage(ITEMS, hasNext = true)

        override suspend fun search(query: String): List<CatalogItem> = ITEMS

        override suspend fun detail(item: CatalogItem): Manga =
            Manga(id = item.id, title = item.title, author = "Eiichiro Oda", sourceId = item.sourceId)
    }

    private class FakeChapters : ChapterRepository {
        override suspend fun chapters(item: CatalogItem): List<DesktopChapter> =
            listOf(DesktopChapter("c1", item.remoteId, 1.0, "Romance Dawn", "MangaPlus", pageCount = 12))

        override suspend fun pages(chapter: DesktopChapter): List<DesktopPage> = emptyList()
    }

    private fun render(width: Int, height: Int, content: @Composable () -> Unit): ByteArray {
        System.setProperty("skiko.renderApi", "SOFTWARE")
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            YomiraDesktopTheme { content() }
        }
        return try {
            val image = scene.render()
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encoding failed")
        } finally {
            scene.close()
        }
    }

    private fun save(name: String, bytes: ByteArray) {
        val directory = File("build/reports/ui").apply { mkdirs() }
        File(directory, name).writeBytes(bytes)
        assertTrue("render must not be an empty frame", bytes.size > 10_000)
    }

    @Test
    fun `browse shell renders at 2K and 4K with the poster grid`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.start()
        testScheduler.advanceUntilIdle()

        val png = render(2560, 1440) {
            YomiraDesktopApp(
                state = state,
                client = okhttp3.OkHttpClient(),
                isFullscreen = false,
                onToggleFullscreen = {},
                searchFocusRequester = androidx.compose.ui.focus.FocusRequester(),
            )
        }
        save("browse-2560x1440.png", png)

        val uhd = render(3840, 2160) {
            YomiraDesktopApp(
                state = state,
                client = okhttp3.OkHttpClient(),
                isFullscreen = true,
                onToggleFullscreen = {},
                searchFocusRequester = androidx.compose.ui.focus.FocusRequester(),
            )
        }
        save("browse-3840x2160-fullscreen.png", uhd)
    }

    @Test
    fun `detail dossier renders its chapter spine`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.start()
        state.openDetail(ITEMS.first())
        testScheduler.advanceUntilIdle()

        val png = render(2560, 1440) {
            YomiraDesktopApp(
                state = state,
                client = okhttp3.OkHttpClient(),
                isFullscreen = false,
                onToggleFullscreen = {},
                searchFocusRequester = androidx.compose.ui.focus.FocusRequester(),
            )
        }
        save("detail-2560x1440.png", png)
    }

    private companion object {
        val ITEMS = listOf(
            CatalogItem(
                id = "mangadex:1", title = "One Piece", sourceId = "mangadex", remoteId = "1",
                status = MangaStatus.ONGOING, year = 1997, score = 9.2,
                synopsis = "Korsanlar Ã§aÄŸÄ±nÄ±n en bÃ¼yÃ¼k hazinesi, tek parÃ§a halinde bekliyor.",
                genres = listOf("Action", "Adventure", "Comedy"),
            ),
            CatalogItem("mangadex:2", "Berserk", sourceId = "mangadex", remoteId = "2", score = 9.5, year = 1989),
            CatalogItem("mangadex:3", "Vagabond", sourceId = "mangadex", remoteId = "3", score = 9.3, year = 1998),
            CatalogItem("mangadex:4", "Solo Leveling", sourceId = "mangadex", remoteId = "4", score = 8.7, year = 2018),
            CatalogItem("mangadex:5", "Chainsaw Man", sourceId = "mangadex", remoteId = "5", score = 9.0, year = 2018),
            CatalogItem("mangadex:6", "Oyasumi Punpun", sourceId = "mangadex", remoteId = "6", score = 9.1, year = 2007),
            CatalogItem("mangadex:7", "Vinland Saga", sourceId = "mangadex", remoteId = "7", score = 9.0, year = 2005),
            CatalogItem("mangadex:8", "Monster", sourceId = "mangadex", remoteId = "8", score = 9.2, year = 1994),
            CatalogItem("mangadex:9", "20th Century Boys", sourceId = "mangadex", remoteId = "9", score = 9.1, year = 1999),
            CatalogItem("mangadex:10", "Frieren", sourceId = "mangadex", remoteId = "10", score = 9.0, year = 2020),
        )
    }
}

