package com.yomira.data.extension

import kotlinx.coroutines.test.runTest
import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import org.junit.Assert.*
import org.junit.Test

class EmbeddedRepositoriesTest {
    @Test fun `packaged catalogs are available before refresh and need no network or URL setup`() = runTest {
        val manager = ExtensionManager(object : RepositoryStore {
            override suspend fun read() = emptyList<String>()
            override suspend fun write(urls: List<String>) = Unit
        }, RepositoryClient { error("Bundled indexes must not use the network") }, KeiyoushiRepoParser(),
            InstalledExtensionProvider { emptyList() })
        assertEquals(3, manager.repositories().size)
        val available = manager.available()
        assertTrue(available.any { it.language == "tr" })
        assertTrue(available.any { it.language == "en" })
        assertEquals(available, manager.refresh())
        assertEquals(available.size, available.map { it.packageName }.distinct().size)
        val state = OnboardingWizardManager(manager, object : OnboardingStore {
            override suspend fun read(): OnboardingState? = null
            override suspend fun write(state: OnboardingState) = Unit
        }).complete(ReaderPreferences(ReadingLanguage.TURKISH), false)
        assertTrue(state.suggestedPackages.isNotEmpty())
    }
}
