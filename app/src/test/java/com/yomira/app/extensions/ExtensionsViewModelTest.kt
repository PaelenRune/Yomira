package com.yomira.app.extensions

import com.yomira.app.catalog.NativeSourceRepository
import com.yomira.domain.extension.*
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionsViewModelTest {
    private val entry = ExtensionDescriptor("example", "Manga-TR", "tr", false, "2", 2,
        "https://example.org/apk/example.apk", listOf(ExtensionSource("1", "Source alias", "tr")))
    private val url = "https://example.org/index.json"
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `clean install is empty and does not fetch or install`() = runBlocking {
        val repo = FakeRepository()
        val model = ExtensionsViewModel(repo, noOperations)
        model.load()
        val state = model.await { !it.loading }
        assertTrue(state.repositories.isEmpty())
        assertTrue(state.available.isEmpty())
        assertTrue(state.installed.isEmpty())
        assertTrue(state.rows.isEmpty())
        assertEquals(0, repo.refreshCalls)
    }

    @Test fun `filters search source names installed updates and languages`() = runBlocking<Unit> {
        val repo = FakeRepository().apply {
            urls = listOf(url)
            entries = listOf(entry, entry.copy(packageName = "english", language = "en", sources = emptyList()))
            installs = listOf(InstalledExtension("example", 1, true, entry.copy(versionCode = 1)))
        }
        val model = ExtensionsViewModel(repo, noOperations)
        model.load()
        model.await { it.rows.size == 2 }
        model.setQuery("ALIAS")
        model.await { it.rows.size == 1 && it.rows.single().descriptor == entry }
        model.setQuery("")
        model.setFilter(ExtensionFilter.INSTALLED)
        model.await { it.filter == ExtensionFilter.INSTALLED && it.rows.size == 1 }
        model.setFilter(ExtensionFilter.UPDATES)
        assertEquals(ExtensionAction.UPDATE, model.await { it.rows.size == 1 }.rows.single().action)
        model.setFilter(ExtensionFilter.ALL)
        model.setLanguage("en")
        model.await { it.rows.singleOrNull()?.descriptor?.packageName == "english" }
        model.setQuery("missing")
        model.await { it.rows.isEmpty() }
    }

    @Test fun `orphaned installations stay removable and native adapter is protected`() = runBlocking {
        val repo = FakeRepository().apply {
            urls = listOf(url)
            installs = listOf(InstalledExtension("example", 2, true, entry))
            entries = listOf(NativeSourceRepository.mangaDex)
        }
        val model = ExtensionsViewModel(repo, noOperations)
        model.load()
        val state = model.await { it.rows.size == 2 }
        assertEquals(ExtensionAction.UNINSTALL, state.rows.first { it.descriptor == entry }.action)
        assertEquals(ExtensionAction.BUILT_IN, state.rows.first { it.descriptor == NativeSourceRepository.mangaDex }.action)
    }

    @Test fun `repository add install update uninstall remove use real UI transitions`() = runBlocking {
        val repo = FakeRepository()
        val operations = object : ExtensionOperations {
            override suspend fun install(descriptor: ExtensionDescriptor, repositoryUrl: String) {
                assertEquals(url, repositoryUrl)
                repo.installs = listOf(InstalledExtension(descriptor.packageName, descriptor.versionCode, true, descriptor))
            }
            override suspend fun uninstall(packageName: String) { repo.installs = emptyList() }
        }
        val model = ExtensionsViewModel(repo, operations)
        model.addRepository(" $url ")
        model.await { !it.loading && url in it.repositories && it.rows.size == 1 }
        model.act("example")
        assertEquals(ExtensionAction.UNINSTALL, model.await { it.busyPackage == null && it.rows.singleOrNull()?.action == ExtensionAction.UNINSTALL }.rows.single().action)
        repo.entries = listOf(entry.copy(versionCode = 3, versionName = "3"))
        model.refresh()
        assertEquals(ExtensionAction.UPDATE, model.await { !it.loading && it.rows.singleOrNull()?.descriptor?.versionCode == 3L }.rows.single().action)
        model.act("example")
        model.await { it.busyPackage == null && it.rows.singleOrNull()?.installedVersion == 3L }
        model.removeRepository(url)
        assertEquals(ExtensionAction.UNINSTALL, model.await { !it.loading && it.repositories.isEmpty() }.rows.single().action)
        model.act("example")
        assertTrue(model.await { it.busyPackage == null && it.installed.isEmpty() && it.rows.isEmpty() }.rows.isEmpty())
    }

    @Test fun `network and install failures retain truthful state and allow retry`() = runBlocking {
        val repo = FakeRepository().apply { urls = listOf(url); fail = true; installs = listOf(InstalledExtension("example", 1, true, entry.copy(versionCode = 1))) }
        val model = ExtensionsViewModel(repo, object : ExtensionOperations {
            override suspend fun install(descriptor: ExtensionDescriptor, repositoryUrl: String) { throw IOException("download") }
            override suspend fun uninstall(packageName: String) = Unit
        })
        model.refresh()
        assertEquals(1L, model.await { it.error && !it.loading }.installed.single().versionCode)
        repo.fail = false
        model.refresh()
        model.await { !it.error && !it.loading && it.rows.singleOrNull()?.action == ExtensionAction.UPDATE }
        model.act("example")
        assertEquals(1L, model.await { it.error && it.busyPackage == null }.installed.single().versionCode)
    }

    @Test fun `invalid repository URL is rejected and revoked consent cannot install`() = runBlocking {
        val repo = FakeRepository()
        var calls = 0
        val model = ExtensionsViewModel(repo, object : ExtensionOperations {
            override suspend fun install(descriptor: ExtensionDescriptor, repositoryUrl: String) { calls++ }
            override suspend fun uninstall(packageName: String) { calls++ }
        })
        model.addRepository("http://example.org/index.json")
        assertTrue(model.await { it.error && !it.loading }.repositories.isEmpty())
        model.addRepository(url)
        model.await { !it.loading && !it.error && it.rows.size == 1 }
        repo.urls = emptyList() // Consent revoked by another surface after the list was rendered.
        model.act("example")
        model.await { it.error && it.busyPackage == null }
        assertEquals(0, calls)
    }

    private suspend fun ExtensionsViewModel.await(predicate: (ExtensionsUiState) -> Boolean) = withTimeout(5_000) { state.first(predicate) }

    private val noOperations = object : ExtensionOperations {
        override suspend fun install(descriptor: ExtensionDescriptor, repositoryUrl: String) = Unit
        override suspend fun uninstall(packageName: String) = Unit
    }

    @Test fun `load uses cache without refreshing and row reads do not rebuild the catalog`() = runBlocking<Unit> {
        val caller = Thread.currentThread()
        val accessThreads = java.util.concurrent.CopyOnWriteArrayList<Thread>()
        val repo = FakeRepository().apply {
            urls = listOf(url)
            fail = true // A network refresh would fail; cached loading must succeed offline.
            entries = object : AbstractList<ExtensionDescriptor>() {
                override val size = 10_000
                override fun get(index: Int): ExtensionDescriptor {
                    accessThreads.add(Thread.currentThread())
                    return entry.copy(packageName = "extension-$index")
                }
            }
        }
        val model = ExtensionsViewModel(repo, noOperations)
        model.load()
        val state = model.await { !it.loading && it.rows.size == 10_000 }
        assertFalse(state.error)
        assertEquals(0, repo.refreshCalls)
        assertTrue(accessThreads.isNotEmpty())
        assertTrue(accessThreads.none { it === caller })
        val accesses = accessThreads.size
        repeat(100) { assertSame(state.rows, state.rows) }
        assertEquals(accesses, accessThreads.size)
        model.setQuery("missing")
        model.setQuery("ALIAS")
        model.setLanguage("en")
        model.await { it.rows.isEmpty() }
        model.setLanguage("tr")
        model.await { it.rows.size == 10_000 }
    }

    private inner class FakeRepository : ExtensionRepository {
        var urls = emptyList<String>()
        var entries = listOf(entry)
        var installs = emptyList<InstalledExtension>()
        var fail = false
        var refreshCalls = 0
        override suspend fun addRepository(indexUrl: String) { requireHttps(indexUrl); urls = (urls + indexUrl).distinct() }
        override suspend fun removeRepository(indexUrl: String) { urls = urls - indexUrl }
        override suspend fun repositories() = urls
        override suspend fun available() = if (urls.isEmpty()) emptyList() else entries
        override suspend fun refresh(): List<ExtensionDescriptor> { refreshCalls++; if (fail) throw IOException("offline"); return available() }
        override suspend fun installed() = installs
        override suspend fun repositoryUrl(packageName: String) = urls.firstOrNull()
    }
}
