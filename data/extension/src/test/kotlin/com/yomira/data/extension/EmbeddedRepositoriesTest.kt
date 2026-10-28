package com.yomira.data.extension

import com.yomira.domain.extension.InstalledExtension
import com.yomira.domain.extension.OnboardingWizardManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EmbeddedRepositoriesTest {
    private val parser = KeiyoushiRepoParser()

    @Test fun `personal build ships three curated repositories`() {
        val embedded = EmbeddedRepositories.load()
        assertEquals(
            setOf(
                OnboardingWizardManager.KEIYOUSHI_INDEX,
                OnboardingWizardManager.TURKISH_INDEX,
                OnboardingWizardManager.WESTERN_INDEX,
            ),
            embedded.map { it.indexUrl }.toSet(),
        )
        assertEquals(listOf(OnboardingWizardManager.KEIYOUSHI_INDEX), EmbeddedRepositories.DEFAULTS)
        val universal = parser.parse(entry(OnboardingWizardManager.KEIYOUSHI_INDEX).payload, OnboardingWizardManager.KEIYOUSHI_INDEX)
        assertTrue("Universal snapshot must carry the full catalog", universal.size >= 1_000)
        assertEquals(universal.size, universal.map { it.packageName }.distinct().size)
        val turkish = parser.parse(entry(OnboardingWizardManager.TURKISH_INDEX).payload, OnboardingWizardManager.TURKISH_INDEX)
        assertTrue("Turkish slice must be substantial", turkish.size >= 50)
        assertTrue(turkish.all { e -> e.language == "tr" || e.sources.any { it.language == "tr" } })
        val western = parser.parse(entry(OnboardingWizardManager.WESTERN_INDEX).payload, OnboardingWizardManager.WESTERN_INDEX)
        assertTrue("Western slice must be substantial", western.size >= 15)
        // The slices are curated subsets of the universal catalog; the manager lets them yield.
        val universalPackages = universal.map { it.packageName }.toSet()
        assertTrue(turkish.all { it.packageName in universalPackages })
        assertTrue(western.all { it.packageName in universalPackages })
    }

    @Test fun `fresh install lists curated extensions with no network at all`() = runTest {
        val urls = EmbeddedRepositories.load().map { it.indexUrl }
        val manager = ExtensionManager(object : RepositoryStore {
            override suspend fun read() = urls
            override suspend fun write(urls: List<String>) = Unit
        }, RepositoryClient { error("Offline: no network may be used") }, parser,
            InstalledExtensionProvider { emptyList() })
        val universalCount = parser.parse(entry(OnboardingWizardManager.KEIYOUSHI_INDEX).payload,
            OnboardingWizardManager.KEIYOUSHI_INDEX).size
        assertTrue(manager.available().isNotEmpty())
        val refreshed = manager.refresh()
        assertEquals("Registered slices must not duplicate universal packages", universalCount, refreshed.size)
        assertEquals(refreshed.size, refreshed.map { it.packageName }.distinct().size)
    }

    @Test fun `empty store keeps the catalog empty and never contacts the client`() = runTest {
        val manager = ExtensionManager(object : RepositoryStore {
            override suspend fun read() = emptyList<String>()
            override suspend fun write(urls: List<String>) = Unit
        }, RepositoryClient { error("No repository was consented to") }, parser,
            InstalledExtensionProvider { listOf(InstalledExtension("user.installed", 1, true)) })
        assertTrue(manager.repositories().isEmpty())
        assertTrue(manager.available().isEmpty())
        assertTrue(manager.refresh().isEmpty())
        assertEquals("user.installed", manager.installed().single().packageName)
    }

    @Test fun `no encrypted scraper payload from the v7 format is packaged`() {
        for (group in listOf("turkish", "western", "global")) {
            assertNull(EmbeddedRepositories::class.java.getResource("/yomira/repositories/$group/index.enc"))
        }
    }

    private fun entry(indexUrl: String): EmbeddedRepository =
        EmbeddedRepositories.load().first { it.indexUrl == indexUrl }
}
