package com.yomira.data.extension

import com.yomira.domain.extension.InstalledExtension
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExtensionManagerTest {
    private val entry = """[{"name":"Example","pkgName":"example","apk":"example.apk","lang":"tr","nsfw":0,"version":"2","code":2,"sources":[]}]"""

    @Test fun `repository additions are idempotent and updates compare version codes`() = runTest {
        val manager = manager { entry }
        manager.addRepository("https://example.org/index.json")
        manager.addRepository("https://example.org/index.json")
        assertEquals(1, manager.repositories().size)
        manager.refresh()
        assertEquals("https://example.org/index.json", manager.repositoryUrl("example"))
        assertEquals(2L, manager.updates().single().available.versionCode)
    }

    @Test fun `failed refresh retains prior snapshot`() = runTest {
        var response = entry
        val manager = manager { response }
        manager.addRepository("https://example.org/index.json")
        val previous = manager.refresh()
        response = "{}"
        try {
            manager.refresh()
            fail("Expected invalid index")
        } catch (_: IllegalArgumentException) { }
        assertEquals(previous, manager.available())
        assertEquals("https://example.org/index.json", manager.repositoryUrl("example"))
    }

    @Test fun `removing repository clears revoked cached entries`() = runTest {
        val manager = manager { entry }
        val url = "https://example.org/index.json"
        manager.addRepository(url)
        manager.refresh()
        manager.removeRepository(url)
        assertTrue(manager.available().isEmpty())
        assertTrue(manager.repositories().isEmpty())
        assertNull(manager.repositoryUrl("example"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `package conflicts across repositories are not silently trusted`() = runTest {
        val manager = manager { entry }
        manager.addRepository("https://one.example.org/index.json")
        manager.addRepository("https://two.example.org/index.json")
        manager.refresh()
    }

    private fun manager(fetch: suspend (String) -> String) = ExtensionManager(
        object : RepositoryStore {
            var urls = emptyList<String>()
            override suspend fun read() = urls
            override suspend fun write(urls: List<String>) { this.urls = urls }
        },
        RepositoryClient(fetch), KeiyoushiRepoParser(),
        InstalledExtensionProvider { listOf(InstalledExtension("example", 1, true)) },
    )
}
