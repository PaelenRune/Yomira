package com.yomira.app.settings

import com.yomira.app.update.AppUpdateChecker
import com.yomira.core.model.ReadingMode
import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.InstalledExtension
import com.yomira.feature.settings.AppCacheCleaner
import com.yomira.feature.settings.AppSettings
import com.yomira.feature.settings.CuratedRepositories
import com.yomira.feature.settings.SettingsStore
import com.yomira.feature.settings.SettingsUiState
import com.yomira.feature.settings.UpdateCheckStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val scheduler = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(scheduler) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `hiding the vault persists immediately`() = runTest(scheduler) {
        val store = MemorySettingsStore()
        val model = model(store = store)
        advanceUntilIdle()

        model.setVaultVisible(false)
        advanceUntilIdle()

        assertFalse(model.settings.value.vaultVisible)
        assertFalse(store.snapshot().vaultVisible)
    }

    @Test fun `setVaultVisible cannot reveal the vault by itself`() = runTest(scheduler) {
        val store = MemorySettingsStore(AppSettings(vaultVisible = false))
        val model = model(store = store)
        advanceUntilIdle()

        model.setVaultVisible(true)
        advanceUntilIdle()

        // Revealing is only reachable through the credential verdict.
        assertFalse(model.settings.value.vaultVisible)
    }

    @Test fun `denied credential keeps the vault hidden and flags the denial`() = runTest(scheduler) {
        val store = MemorySettingsStore(AppSettings(vaultVisible = false))
        val model = model(store = store)
        val states = model.recordStates(this)
        advanceUntilIdle()

        model.onVaultRevealResult(verified = false)
        advanceUntilIdle()

        assertFalse(model.settings.value.vaultVisible)
        assertTrue(states.last().revealDenied)
    }

    @Test fun `verified credential reveals the vault and clears the denial`() = runTest(scheduler) {
        val store = MemorySettingsStore(AppSettings(vaultVisible = false))
        val model = model(store = store)
        val states = model.recordStates(this)
        advanceUntilIdle()
        model.onVaultRevealResult(verified = false)
        advanceUntilIdle()

        model.onVaultRevealResult(verified = true)
        advanceUntilIdle()

        assertTrue(model.settings.value.vaultVisible)
        assertTrue(store.snapshot().vaultVisible)
        assertFalse(states.last().revealDenied)
    }

    @Test fun `privacy and reading toggles persist`() = runTest(scheduler) {
        val store = MemorySettingsStore()
        val model = model(store = store)
        advanceUntilIdle()

        model.setRequireDeviceLock(false)
        model.setLockOnBackground(false)
        model.setReadingMode(ReadingMode.WESTERN_LTR)
        advanceUntilIdle()

        val settings = store.snapshot()
        assertFalse(settings.requireDeviceLock)
        assertFalse(settings.lockOnBackground)
        assertEquals(ReadingMode.WESTERN_LTR, settings.readingMode)
    }

    @Test fun `enabling a curated repository registers and persists it`() = runTest(scheduler) {
        val extensions = FakeExtensions()
        val model = model(extensions = extensions)
        val states = model.recordStates(this)
        advanceUntilIdle()

        model.setRepositoryEnabled(CuratedRepositories.TURKISH_COMICS, true)
        advanceUntilIdle()

        assertEquals(listOf(CuratedRepositories.TURKISH_COMICS.indexUrl), extensions.added)
        assertTrue(CuratedRepositories.TURKISH_COMICS.indexUrl in model.state.value.repositories)
        assertTrue(states.last().repositories.contains(CuratedRepositories.TURKISH_COMICS.indexUrl))
    }

    @Test fun `disabling a curated repository revokes the registration`() = runTest(scheduler) {
        val extensions = FakeExtensions()
        val model = model(extensions = extensions)
        val states = model.recordStates(this)
        advanceUntilIdle()
        model.setRepositoryEnabled(CuratedRepositories.UNIVERSAL, true)
        advanceUntilIdle()

        model.setRepositoryEnabled(CuratedRepositories.UNIVERSAL, false)
        advanceUntilIdle()

        assertEquals(listOf(CuratedRepositories.UNIVERSAL.indexUrl), extensions.removed)
        assertFalse(model.state.value.repositories.contains(CuratedRepositories.UNIVERSAL.indexUrl))
        assertFalse(states.last().repositories.contains(CuratedRepositories.UNIVERSAL.indexUrl))
    }

    @Test fun `a repository whose index cannot be fetched stays registered`() = runTest(scheduler) {
        val extensions = FakeExtensions { throw java.io.IOException("offline") }
        val model = model(extensions = extensions)
        val states = model.recordStates(this)
        advanceUntilIdle()

        model.setRepositoryEnabled(CuratedRepositories.WESTERN_COMICS, true)
        advanceUntilIdle()

        // Registration is the success criterion; the offline refresh must not undo it.
        assertTrue(model.state.value.repositories.contains(CuratedRepositories.WESTERN_COMICS.indexUrl))
        assertFalse(states.last().repositoryError)
    }

    @Test fun `custom repository demands a full https url`() = runTest(scheduler) {
        val extensions = FakeExtensions()
        val model = model(extensions = extensions)
        val states = model.recordStates(this)
        advanceUntilIdle()

        model.addCustomRepository("http://insecure.example.org/index.min.json")
        advanceUntilIdle()

        assertTrue(extensions.added.isEmpty())
        assertTrue(states.last().invalidRepositoryUrl)
        assertFalse(model.state.value.repositories.contains("http://insecure.example.org/index.min.json"))

        model.addCustomRepository("https://custom.example.org/index.min.json")
        advanceUntilIdle()

        assertEquals(listOf("https://custom.example.org/index.min.json"), extensions.added)
        assertTrue(model.state.value.repositories.contains("https://custom.example.org/index.min.json"))
    }

    @Test fun `removing a custom repository drops only that url`() = runTest(scheduler) {
        val extensions = FakeExtensions()
        val model = model(extensions = extensions)
        val states = model.recordStates(this)
        advanceUntilIdle()
        model.setRepositoryEnabled(CuratedRepositories.UNIVERSAL, true)
        model.addCustomRepository("https://custom.example.org/index.min.json")
        advanceUntilIdle()

        model.removeRepository("https://custom.example.org/index.min.json")
        advanceUntilIdle()

        val repositories = model.state.value.repositories
        assertTrue(CuratedRepositories.UNIVERSAL.indexUrl in repositories)
        assertFalse("https://custom.example.org/index.min.json" in repositories)
        assertEquals(repositories, states.last().repositories)
    }

    @Test fun `cache clearing reports the freed bytes`() = runTest(scheduler) {
        val model = model(cacheCleaner = AppCacheCleaner { 1536 })
        val states = model.recordStates(this)
        advanceUntilIdle()

        model.clearCache()
        advanceUntilIdle()

        assertEquals(1536L, states.last().cacheClearedBytes)
        assertFalse(states.last().cacheFailed)
    }

    @Test fun `failing cache cleaner surfaces the failure flag`() = runTest(scheduler) {
        val model = model(cacheCleaner = AppCacheCleaner { throw java.io.IOException("locked") })
        val states = model.recordStates(this)
        advanceUntilIdle()

        model.clearCache()
        advanceUntilIdle()

        assertTrue(states.last().cacheFailed)
        assertEquals(null, states.last().cacheClearedBytes)
    }

    @Test fun `update check surfaces an available release`() = runTest(scheduler) {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.2.0-beta")))
            val model = model(updateChecker = checker(server))
            model.recordStates(this)
            advanceUntilIdle()

            model.checkUpdates()
            model.awaitUpdateCheck(this)

            val status = model.state.value.updateCheck
            assertTrue("expected Available but was $status", status is UpdateCheckStatus.Available)
            assertEquals("v0.2.0-beta", (status as UpdateCheckStatus.Available).version)
        }
    }

    @Test fun `update check collapses missing releases to up to date`() = runTest(scheduler) {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            val model = model(updateChecker = checker(server))
            model.recordStates(this)
            advanceUntilIdle()

            model.checkUpdates()
            model.awaitUpdateCheck(this)

            assertEquals(UpdateCheckStatus.UpToDate, model.state.value.updateCheck)
        }
    }

    private fun model(
        store: SettingsStore = MemorySettingsStore(),
        extensions: FakeExtensions = FakeExtensions(),
        cacheCleaner: AppCacheCleaner = AppCacheCleaner { 0 },
        updateChecker: AppUpdateChecker = checker(MockWebServer().apply { enqueue(MockResponse().setResponseCode(404)) }),
    ) = SettingsViewModel(store, extensions, cacheCleaner, updateChecker)

    /**
     * Keeps [SettingsViewModel.state] subscribed for the whole test â€” `state` is
     * WhileSubscribed, so without a collector it would never leave its initial
     * value. backgroundScope cancels the collector automatically when the test
     * body finishes, avoiding UncompletedCoroutinesError.
     */
    private fun SettingsViewModel.recordStates(scope: TestScope): List<SettingsUiState> {
        val collected = mutableListOf<SettingsUiState>()
        scope.backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(scope.testScheduler)) {
            state.collect { collected.add(it) }
        }
        return collected
    }

    /**
     * The update checker performs a real HTTP call against MockWebServer on
     * Dispatchers.IO; virtual-time advancing alone cannot observe its
     * completion, so poll with real-time sleeps until the status settles.
     */
    private suspend fun SettingsViewModel.awaitUpdateCheck(scope: TestScope) {
        scope.advanceUntilIdle()
        val deadline = System.currentTimeMillis() + 10_000
        while (state.value.updateCheck == UpdateCheckStatus.Checking || state.value.updateCheck == UpdateCheckStatus.Idle) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("update check never settled")
            withContext(Dispatchers.Default) { delay(20) }
            scope.advanceUntilIdle()
        }
    }

    private fun checker(server: MockWebServer) = AppUpdateChecker(
        httpClient = OkHttpClient(),
        currentVersion = "0.1.0-beta",
        endpoint = server.url("/releases/latest"),
    )

    private fun release(
        tag: String,
        draft: Boolean = false,
        body: String = "Performans ve kararlÄ±lÄ±k iyileÅŸtirmeleri.",
    ) = """
        {"tag_name":"$tag","name":"$tag","body":"$body","html_url":"https://github.com/PaelenRune/Yomira/releases/tag/$tag",
        "draft":$draft,"prerelease":false,
        "assets":[{"name":"yomira.apk","browser_download_url":"https://github.com/PaelenRune/Yomira/releases/download/$tag/yomira.apk"}]}
    """.trimIndent()

    private class MemorySettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
        private val current = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = current
        override suspend fun read(): AppSettings = current.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) { current.value = transform(current.value) }
        fun snapshot(): AppSettings = current.value
    }

    private class FakeExtensions(
        private val refreshResult: suspend () -> List<ExtensionDescriptor> = { emptyList() },
    ) : ExtensionRepository {
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        private val urls = mutableSetOf<String>()
        override suspend fun addRepository(indexUrl: String) { added += indexUrl; urls += indexUrl }
        override suspend fun removeRepository(indexUrl: String) { removed += indexUrl; urls -= indexUrl }
        override suspend fun repositories(): List<String> = urls.toList()
        override suspend fun refresh(): List<ExtensionDescriptor> = refreshResult()
        override suspend fun available(): List<ExtensionDescriptor> = emptyList()
        override suspend fun installed(): List<InstalledExtension> = emptyList()
    }
}
