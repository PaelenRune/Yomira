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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Headless render smoke test.
 *
 * It composes the real shell at every target form factor — phone 412x915,
 * tablet 1280x800 and 800x1280, desktop 2K and 4K — and writes the frames to
 * `desktop/build/reports/ui/` for visual review. The assertions catch the
 * failures that unit tests cannot: a Canvas size mismatch, a broken typography
 * resource, a modifier crash in the grid, or a Compose runtime error while the
 * detail/reader layers are mounted.
 *
 * No frame renders with empty posters: the fakes below pair every catalogue
 * title with the repository's own colourful fixture artwork (`art/showcase/`,
 * served through [RemoteImages]'s `asset://` scheme from local data), and
 * [render] advances the animation clock until each crossfade settles. One
 * Piece, Berserk, Solo Leveling, Chainsaw Man and the rest therefore appear
 * with their real colour posters in every saved frame, through the same
 * [RemoteImage] pipeline production uses for HTTPS artwork.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopRenderSmokeTest {

    private class FakeCatalog : CatalogRepository {
        override suspend fun browse(cursor: com.yomira.desktop.state.PageCursor, pageSize: Int): CatalogPage =
            CatalogPage(ITEMS, hasNext = true)

        override suspend fun search(query: String): List<CatalogItem> = ITEMS

        override suspend fun detail(item: CatalogItem): Manga =
            Manga(
                id = item.id,
                title = item.title,
                coverUrl = item.coverUrl,
                bannerUrl = item.bannerUrl,
                author = "Eiichiro Oda",
                synopsis = item.synopsis,
                score = item.score,
                status = item.status,
                genres = item.genres,
                sourceId = item.sourceId,
            )
    }

    private class FakeChapters : ChapterRepository {
        override suspend fun chapters(item: CatalogItem): List<DesktopChapter> =
            listOf(
                DesktopChapter("c1", item.remoteId, 1.0, "Romance Dawn", "MangaPlus", pageCount = 12),
                DesktopChapter("c2", item.remoteId, 2.0, "Buggy the Clown", "MangaPlus", pageCount = 14),
            )

        override suspend fun pages(chapter: DesktopChapter): List<DesktopPage> =
            (0 until 6).map { index ->
                // Original showcase pages instead of unreachable URLs, so the
                // reader frames stream real colour panels through the loader.
                DesktopPage(index = index, url = ShowcaseAssets.page("solo-leveling", index % 4 + 1))
            }
    }

    private fun render(width: Int, height: Int, content: @Composable () -> Unit): ByteArray {
        System.setProperty("skiko.renderApi", "SOFTWARE")
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            YomiraDesktopTheme { content() }
        }
        return try {
            var bytes: ByteArray? = null
            // Artwork resolves through an async `produceState` and the crossfade
            // then needs a few frames. Advance the animation clock until every
            // poster has faded in, then encode the final settled bitmap.
            repeat(8) { frame ->
                Thread.sleep(90)
                val image = scene.render(frame * 120_000_000L)
                assertEquals(width, image.width)
                assertEquals(height, image.height)
                if (frame == 7) bytes = image.encodeToData(EncodedImageFormat.PNG)?.bytes
            }
            bytes ?: error("PNG encoding failed")
        } finally {
            scene.close()
        }
    }

    private fun save(name: String, bytes: ByteArray) {
        val directory = File("build/reports/ui").apply { mkdirs() }
        File(directory, name).writeBytes(bytes)
        assertTrue("render must not be an empty frame", bytes.size > 10_000)
    }

    /** Renders the full shell (masthead + current screen) at the given canvas size. */
    private fun renderShell(state: DesktopAppState, width: Int, height: Int): ByteArray =
        render(width, height) {
            YomiraDesktopApp(
                state = state,
                client = okhttp3.OkHttpClient(),
                isFullscreen = false,
                onToggleFullscreen = {},
                searchFocusRequester = androidx.compose.ui.focus.FocusRequester(),
            )
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

    @Test
    fun `phone shell renders at 412x915 portrait`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.start()
        testScheduler.advanceUntilIdle()

        save("phone-412x915-browse.png", renderShell(state, 412, 915))

        state.openDetail(ITEMS.first())
        testScheduler.advanceUntilIdle()
        save("phone-412x915-detail.png", renderShell(state, 412, 915))
    }

    @Test
    fun `tablet shell renders at 1280x800 landscape`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.start()
        testScheduler.advanceUntilIdle()

        save("tablet-1280x800-browse.png", renderShell(state, 1280, 800))

        state.openDetail(ITEMS.first())
        testScheduler.advanceUntilIdle()
        save("tablet-1280x800-detail.png", renderShell(state, 1280, 800))
    }

    @Test
    fun `tablet shell renders at 800x1280 portrait`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.start()
        testScheduler.advanceUntilIdle()

        save("tablet-800x1280-browse.png", renderShell(state, 800, 1280))
    }

    @Test
    fun `webtoon reader renders its floating capsules`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.start()
        state.openDetail(ITEMS.first())
        testScheduler.advanceUntilIdle()
        state.openChapter(state.chapterList.first())
        testScheduler.advanceUntilIdle()

        save("reader-2560x1440-webtoon.png", renderShell(state, 2560, 1440))
        save("reader-412x915-webtoon.png", renderShell(state, 412, 915))
    }

    @Test
    fun `settings panel renders its toggles and key map`() = runTest {
        val state = DesktopAppState(FakeCatalog(), FakeChapters(), this)
        state.toggleReadableOnly()

        val png = render(340, 480) {
            SettingsPanel(
                state = state,
                isFullscreen = false,
                onToggleFullscreen = {},
                onClose = {},
            )
        }
        save("settings-340x480.png", png)
    }

    companion object {
        /**
         * Local colour artwork for every frame this suite composes. Pointing
         * [RemoteImages.assetRoot] at the repository `art/` folder activates the
         * `asset://` scheme (it never touches the network), and decoding every
         * fixture into [RemoteImages]'s process cache up front keeps the frames
         * deterministic: each poster starts the composition already resolved
         * instead of racing the async image producer.
         */
        @JvmStatic
        @BeforeClass
        fun installFixtureArtwork() {
            ShowcaseAssets.install()
            val urls = ITEMS.flatMap { listOfNotNull(it.coverUrl, it.bannerUrl) } +
                (1..4).map { ShowcaseAssets.page("solo-leveling", it) }
            runBlocking {
                val client = okhttp3.OkHttpClient()
                urls.forEach { url ->
                    check(RemoteImages.load(client, url) != null) { "Fixture artwork did not decode: $url" }
                }
            }
        }

        /** Ten catalogue labels, each wired to its fixture poster via `asset://`. */
        val ITEMS = listOf(
            CatalogItem(
                id = "mangadex:1", title = "One Piece", sourceId = "mangadex", remoteId = "1",
                coverUrl = ShowcaseAssets.cover("one-piece"),
                bannerUrl = ShowcaseAssets.banner("one-piece"),
                status = MangaStatus.ONGOING, year = 1997, score = 9.2,
                synopsis = "Korsanlar çağının en büyük hazinesi, tek parça halinde bekliyor.",
                genres = listOf("Action", "Adventure", "Comedy"),
            ),
            CatalogItem("mangadex:2", "Berserk", sourceId = "mangadex", remoteId = "2", score = 9.5, year = 1989,
                coverUrl = ShowcaseAssets.cover("berserk")),
            CatalogItem("mangadex:3", "Vagabond", sourceId = "mangadex", remoteId = "3", score = 9.3, year = 1998,
                coverUrl = ShowcaseAssets.cover("vagabond")),
            CatalogItem("mangadex:4", "Solo Leveling", sourceId = "mangadex", remoteId = "4", score = 8.7, year = 2018,
                coverUrl = ShowcaseAssets.cover("solo-leveling")),
            CatalogItem("mangadex:5", "Chainsaw Man", sourceId = "mangadex", remoteId = "5", score = 9.0, year = 2018,
                coverUrl = ShowcaseAssets.cover("chainsaw-man")),
            CatalogItem("mangadex:6", "Oyasumi Punpun", sourceId = "mangadex", remoteId = "6", score = 9.1, year = 2007,
                coverUrl = ShowcaseAssets.cover("oyasumi-punpun")),
            CatalogItem("mangadex:7", "Vinland Saga", sourceId = "mangadex", remoteId = "7", score = 9.0, year = 2005,
                coverUrl = ShowcaseAssets.cover("vinland-saga")),
            CatalogItem("mangadex:8", "Monster", sourceId = "mangadex", remoteId = "8", score = 9.2, year = 1994,
                coverUrl = ShowcaseAssets.cover("monster")),
            CatalogItem("mangadex:9", "20th Century Boys", sourceId = "mangadex", remoteId = "9", score = 9.1, year = 1999,
                coverUrl = ShowcaseAssets.cover("20th-century-boys")),
            CatalogItem("mangadex:10", "Frieren", sourceId = "mangadex", remoteId = "10", score = 9.0, year = 2020,
                coverUrl = ShowcaseAssets.cover("frieren")),
        )
    }
}
