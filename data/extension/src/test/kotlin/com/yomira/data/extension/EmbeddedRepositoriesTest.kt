package com.yomira.data.extension

import com.yomira.domain.extension.InstalledExtension
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EmbeddedRepositoriesTest {
    @Test fun `fresh engine has no repositories catalogs or network bootstrap`() = runTest {
        val manager = ExtensionManager(object : RepositoryStore {
            override suspend fun read() = emptyList<String>()
            override suspend fun write(urls: List<String>) = Unit
        }, RepositoryClient { error("No repository was consented to") }, KeiyoushiRepoParser(),
            InstalledExtensionProvider { listOf(InstalledExtension("user.installed", 1, true)) })
        assertTrue(EmbeddedRepositories.load().isEmpty())
        assertTrue(manager.repositories().isEmpty())
        assertTrue(manager.available().isEmpty())
        assertTrue(manager.refresh().isEmpty())
        assertEquals("user.installed", manager.installed().single().packageName)
    }

    @Test fun `no scraper manifest is packaged`() {
        assertNull(EmbeddedRepositories::class.java.getResource("/assets/keiyoushi/index.min.json"))
        for (group in listOf("turkish", "western", "global")) {
            assertNull(EmbeddedRepositories::class.java.getResource("/yomira/repositories/$group/index.enc"))
        }
    }
}
