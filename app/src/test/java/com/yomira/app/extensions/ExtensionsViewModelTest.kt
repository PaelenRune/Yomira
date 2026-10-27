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

    @Test fun `filters search source names installed updates and languages`() {
        val state = ExtensionsUiState(available = listOf(entry, entry.copy(packageName = "english", language = "en", sources = emptyList())),
            installed = listOf(InstalledExtension("example", 1, true, entry.copy(versionCode = 1))))
        assertEquals(2, state.rows.size)
        assertEquals(1, state.copy(query = "ALIAS").rows.size)
        assertEquals(1, state.copy(filter = ExtensionFilter.INSTALLED).rows.size)
        assertEquals(ExtensionAction.UPDATE, state.copy(filter = ExtensionFilter.UPDATES).rows.single().action)
        assertEquals("english", state.copy(language = "en").rows.single().descriptor.packageName)
        assertTrue(state.copy(query = "missing").rows.isEmpty())
    }

    @Test fun `orphaned installations stay removable and native adapter is protected`() {
        val state = ExtensionsUiState(installed = listOf(InstalledExtension("example", 2, true, entry)),
            available = listOf(NativeSourceRepository.mangaDex))
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
        model.await { !it.loading && url in it.repositories }
        model.act("example")
        assertEquals(ExtensionAction.UNINSTALL, model.await { it.busyPackage == null && it.installed.isNotEmpty() }.rows.single().action)
        repo.entries = listOf(entry.copy(versionCode = 3, versionName = "3"))
        model.refresh()
        assertEquals(ExtensionAction.UPDATE, model.await { !it.loading && it.available.singleOrNull()?.versionCode == 3L }.rows.single().action)
        model.act("example")
        model.await { it.busyPackage == null && it.installed.singleOrNull()?.versionCode == 3L }
        model.removeRepository(url)
        assertEquals(ExtensionAction.UNINSTALL, model.await { !it.loading && it.repositories.isEmpty() }.rows.single().action)
        model.act("example")
        assertTrue(model.await { it.busyPackage == null && it.installed.isEmpty() }.rows.isEmpty())
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
        model.await { !it.error && !it.loading }
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
        model.await { !it.loading && !it.error }
        repo.urls = emptyList() // Consent revoked by another surface after the list was rendered.
        model.act("example")
        model.await { it.error && it.busyPackage == null }
        assertEquals(0, calls)
    }

    private suspend fun ExtensionsViewModel.await(predicate: (ExtensionsUiState) -> Boolean) = withTimeout(5_000) { state.first(predicate) }

    private inner class FakeRepository : ExtensionRepository {
        var urls = emptyList<String>()
        var entries = listOf(entry)
        var installs = emptyList<InstalledExtension>()
        var fail = false
        override suspend fun addRepository(indexUrl: String) { requireHttps(indexUrl); urls = (urls + indexUrl).distinct() }
        override suspend fun removeRepository(indexUrl: String) { urls = urls - indexUrl }
        override suspend fun repositories() = urls
        override suspend fun available() = if (urls.isEmpty()) emptyList() else entries
        override suspend fun refresh(): List<ExtensionDescriptor> { if (fail) throw IOException("offline"); return available() }
        override suspend fun installed() = installs
        override suspend fun repositoryUrl(packageName: String) = urls.firstOrNull()
    }
}
