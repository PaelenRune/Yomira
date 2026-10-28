package com.yomira.app.runtime

import com.yomira.data.extension.ApkSandboxExtractor
import com.yomira.data.extension.ExtensionConsent
import com.yomira.data.extension.ExtensionConsentStore
import com.yomira.data.extension.ExtensionTrust
import com.yomira.data.extension.FileJsonSourceManifestStore
import com.yomira.data.extension.JsonSourceManifest
import com.yomira.data.extension.SandboxIntegrityGuard
import com.yomira.data.extension.SandboxLayout
import com.yomira.data.extension.SandboxedDexFile
import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionSource
import com.yomira.domain.extension.SourceChapter
import com.yomira.domain.extension.SourceManga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Compiled against the :core:extension-api shims, like a real extension source. */
private class FakeRuntimeSource : HttpSource() {
    override val baseUrl: String = "https://fake.example"
    override val name: String = "FakeRuntime"
    override val lang: String = "tr"
    override val supportsLatest: Boolean = false

    var searchedQuery: String? = null
    var updateRequestedFor: String? = null
    var pagesRequestedFor: String? = null
    var imageUrlResolutions = 0

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Referer", "https://fake.example/")
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        searchedQuery = query
        return MangasPage(
            listOf(
                SManga.create().apply {
                    url = "/series/one"
                    title = "One"
                },
                SManga.create().apply {
                    url = "/series/two"
                    title = "Two"
                },
            ),
            hasNextPage = false,
        )
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        updateRequestedFor = manga.url
        return SMangaUpdate(
            manga,
            listOf(
                SChapter.create().apply {
                    url = "/read/one/chapter/1"
                    name = "Chapter 1"
                    chapter_number = 1f
                    date_upload = 1_700_000_000_000L
                },
                SChapter.create().apply {
                    url = "/read/one/chapter/2"
                    name = "Chapter 2"
                    chapter_number = 2.5f
                },
            ),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        pagesRequestedFor = chapter.url
        // Page 0 has no imageUrl: the runtime must resolve it via getImageUrl.
        return listOf(Page(0, "/read/one/chapter/1/p0", null), Page(1, "", "https://fake.example/c1/p1.jpg"))
    }

    override suspend fun getImageUrl(page: Page): String {
        imageUrlResolutions++
        return "https://fake.example/c1/p${page.index}.jpg"
    }
}

private class FakeRuntimeFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(FakeRuntimeSource())
}

/** JVM seam: resolves test-compiled classes instead of loading a dex. */
private class FakeEnvironment : ExtensionRuntimeEnvironment {
    var createdLoaders = 0
    var hostServicesPrepared = 0

    override fun createClassLoader(dexPaths: List<java.nio.file.Path>, optimizedDirectory: java.io.File): ClassLoader {
        createdLoaders++
        return FakeRuntimeFactory::class.java.classLoader
    }

    override fun optimizedDexDirectory(): java.io.File = Files.createTempDirectory("yomira-test").toFile()

    override fun prepareHostServices() {
        hostServicesPrepared++
    }
}

private class RecordingConsentStore : ExtensionConsentStore {
    val records = mutableListOf<ExtensionConsent>()
    override suspend fun all(): List<ExtensionConsent> = records.toList()
    override suspend fun grant(consent: ExtensionConsent) {
        records.removeAll { it.packageName == consent.packageName }
        records += consent
    }

    override suspend fun revoke(packageName: String) {
        records.removeAll { it.packageName == packageName }
    }
}

class AndroidSourceRuntimeTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val packageName = "eu.kanade.tachiyomi.extension.tr.fake"
    private val fakeSourceId = FakeRuntimeSource().id.toString()

    private lateinit var layout: SandboxLayout
    private lateinit var store: FileJsonSourceManifestStore
    private lateinit var guard: SandboxIntegrityGuard
    private lateinit var consent: RecordingConsentStore

    private suspend fun install(installedAt: Long = 1_000L, dexBytes: ByteArray = byteArrayOf(1, 2, 3, 4)): JsonSourceManifest {
        layout = SandboxLayout(temporary.newFolder().toPath())
        store = FileJsonSourceManifestStore(layout)
        guard = SandboxIntegrityGuard(layout, store)
        val dexDirectory = layout.dexDirectory(packageName)
        Files.createDirectories(dexDirectory)
        Files.write(dexDirectory.resolve("classes.dex"), dexBytes)
        val descriptor = ExtensionDescriptor(
            packageName = packageName,
            name = "FakeRuntime",
            language = "tr",
            nsfw = false,
            versionName = "1.0.0",
            versionCode = 1L,
            apkUrl = "https://example.com/fake.apk",
            sources = listOf(ExtensionSource(fakeSourceId, "FakeRuntime", "tr")),
        )
        val manifest = JsonSourceManifest.of(
            descriptor,
            repositoryUrl = "https://example.com/index",
            installedAt = installedAt,
            dexFiles = listOf(SandboxedDexFile("classes.dex", dexBytes.size.toLong(), ApkSandboxExtractor.sha256Hex(dexBytes))),
            entryClasses = listOf(FakeRuntimeFactory::class.java.name),
        )
        store.save(manifest)
        return manifest
    }

    private fun runtime(environment: FakeEnvironment = FakeEnvironment()): AndroidSourceRuntime {
        consent = RecordingConsentStore()
        val trust = ExtensionTrust(consent) { false }
        return AndroidSourceRuntime(guard, trust, OkHttpClient(), environment)
    }

    private suspend fun grantConsent(manifest: JsonSourceManifest) {
        consent.grant(
            ExtensionConsent(
                packageName = manifest.packageName,
                publisherFingerprint = null,
                grantedAt = manifest.installedAt,
                language = "tr",
            ),
        )
    }

    @Test
    fun `consent is required before any dex is loaded`() = runTest {
        val manifest = install()
        val environment = FakeEnvironment()
        val runtime = runtime(environment)
        try {
            runtime.getMangaList(packageName, fakeSourceId, "one")
            fail("Expected consent denial")
        } catch (error: ExtensionConsentDeniedException) {
            assertEquals(0, environment.createdLoaders)
            assertEquals(0, environment.hostServicesPrepared)
        }
        grantConsent(manifest)
        assertTrue(runtime.getMangaList(packageName, fakeSourceId, "one").isNotEmpty())
        assertEquals(1, environment.createdLoaders)
    }

    @Test
    fun `tampered dex fails the integrity guard`() = runTest {
        val manifest = install()
        val runtime = runtime()
        grantConsent(manifest)
        Files.write(layout.dexDirectory(packageName).resolve("classes.dex"), byteArrayOf(9, 9, 9, 9))
        try {
            runtime.getMangaList(packageName, fakeSourceId, "one")
            fail("Expected integrity failure")
        } catch (error: ExtensionIntegrityException) {
            assertTrue(error.cause is SecurityException)
        }
    }

    @Test
    fun `unknown source ids are rejected`() = runTest {
        val manifest = install()
        val runtime = runtime()
        grantConsent(manifest)
        try {
            runtime.verifyAccess(packageName, "1234567890")
            fail("Expected unknown source")
        } catch (error: ExtensionSourceException) {
            assertTrue(error.message!!.contains("1234567890"))
        }
    }

    @Test
    fun `missing manifest fails closed`() = runTest {
        install()
        val runtime = runtime()
        try {
            runtime.verifyAccess("eu.kanade.tachiyomi.extension.tr.absent", fakeSourceId)
            fail("Expected missing sandbox")
        } catch (error: ExtensionIntegrityException) {
            assertTrue(error.cause is SecurityException)
        }
    }

    @Test
    fun `search maps dex results onto the bridge contract`() = runTest {
        val manifest = install()
        val runtime = runtime()
        grantConsent(manifest)
        val results = runtime.getMangaList(packageName, fakeSourceId, "one", page = 2)
        assertEquals(listOf("/series/one", "/series/two"), results.map { it.id })
        assertEquals(listOf("One", "Two"), results.map { it.title })
        assertEquals("https://fake.example/series/one", results.first().url)
    }

    @Test
    fun `chapter list round-trips through the manga url`() = runTest {
        val manifest = install()
        val runtime = runtime()
        grantConsent(manifest)
        val manga = SourceManga("/series/one", "One", "https://fake.example/series/one")
        val chapters = runtime.getChapterList(packageName, fakeSourceId, manga)
        assertEquals(2, chapters.size)
        assertEquals("/read/one/chapter/1", chapters[0].id)
        assertEquals(1.0, chapters[0].number!!, 0.0)
        assertEquals(1_700_000_000_000L, chapters[0].uploadedAt)
        assertEquals(2.5, chapters[1].number!!, 0.0)
        assertEquals(null, chapters[1].uploadedAt)
    }

    @Test
    fun `pages resolve image urls and carry the source request headers`() = runTest {
        val manifest = install()
        val runtime = runtime()
        grantConsent(manifest)
        val pages = runtime.getPageList(packageName, fakeSourceId, SourceChapter("/read/one/chapter/1", "Chapter 1", 1.0, null))
        assertEquals(2, pages.size)
        assertEquals("https://fake.example/c1/p0.jpg", pages[0].imageUrl)
        assertEquals("https://fake.example/c1/p1.jpg", pages[1].imageUrl)
        assertEquals("https://fake.example/", pages[0].headers["Referer"])
        assertTrue(pages[0].headers.containsKey("User-Agent"))
    }

    @Test
    fun `the classloader is cached per package and refreshed on update`() = runTest {
        val manifest = install()
        val environment = FakeEnvironment()
        val runtime = runtime(environment)
        grantConsent(manifest)
        runtime.getMangaList(packageName, fakeSourceId, "one")
        runtime.getChapterList(packageName, fakeSourceId, SourceManga("/series/one", "One", "u"))
        assertEquals(1, environment.createdLoaders)
        assertEquals(1, environment.hostServicesPrepared)

        // A version bump swaps in a new loader even though the package is cached.
        val updatedDex = byteArrayOf(5, 6, 7, 8)
        val dexDirectory = layout.dexDirectory(packageName)
        Files.write(dexDirectory.resolve("classes.dex"), updatedDex)
        val updated = JsonSourceManifest.of(
            manifest.descriptor().copy(versionCode = 2L),
            repositoryUrl = "https://example.com/index",
            installedAt = 2_000L,
            dexFiles = listOf(SandboxedDexFile("classes.dex", updatedDex.size.toLong(), ApkSandboxExtractor.sha256Hex(updatedDex))),
            entryClasses = listOf(FakeRuntimeFactory::class.java.name),
        )
        store.save(updated)
        consent.grant(ExtensionConsent(packageName, null, 2_000L, "tr"))
        runtime.getMangaList(packageName, fakeSourceId, "one")
        assertEquals(2, environment.createdLoaders)
    }

    @Test
    fun `generated keiyoushi entry class is used when the manifest has none`() = runTest {
        val manifest = install()
        // Wipe the entry classes: the runtime must fall back to the fixed
        // keiyoushi.source.Generated name resolved by the (fake) classloader.
        val stripped = manifest.copy(entryClasses = emptyList())
        store.save(stripped)
        val runtime = runtime()
        grantConsent(stripped)
        try {
            runtime.getMangaList(packageName, fakeSourceId, "one")
            fail("Expected a link failure naming the generated entry class")
        } catch (error: ExtensionLinkException) {
            assertTrue(error.message!!.contains(AndroidSourceRuntime.GENERATED_ENTRY_CLASS))
        }
    }
}
