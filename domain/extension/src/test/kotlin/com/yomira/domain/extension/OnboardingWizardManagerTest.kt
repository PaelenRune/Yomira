package com.yomira.domain.extension

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OnboardingWizardManagerTest {
    @Test fun `skipping completes with zero repositories`() = runTest {
        val store = Store()
        val repository = Repository()
        val preferences = ReaderPreferences(ReadingLanguage.ENGLISH, true)
        val result = OnboardingWizardManager(repository, store).complete(preferences, false)
        assertEquals(preferences, result.preferences)
        assertTrue(result.completed)
        assertNull(result.repositoryUrl)
        assertTrue(repository.urls.isEmpty())
        assertFalse(repository.refreshed)
        assertEquals(result, store.state)
    }

    @Test fun `default completion never adds a repository`() = runTest {
        val repository = Repository()
        OnboardingWizardManager(repository, Store()).complete(ReaderPreferences(ReadingLanguage.TURKISH))
        assertTrue(repository.urls.isEmpty())
    }

    @Test fun `explicit add registers only the official index and suggests allowed sources`() = runTest {
        val repository = Repository()
        val result = OnboardingWizardManager(repository, Store()).complete(ReaderPreferences(ReadingLanguage.TURKISH), true)
        assertEquals(listOf(OnboardingWizardManager.KEIYOUSHI_INDEX), repository.urls)
        assertEquals(OnboardingWizardManager.KEIYOUSHI_INDEX, result.repositoryUrl)
        assertEquals(listOf("tr"), result.suggestedPackages)
    }

    @Test fun `failed refresh still completes with the official repository registered`() = runTest {
        val store = Store()
        val repository = Repository(failRefresh = true)
        val result = OnboardingWizardManager(repository, store).complete(ReaderPreferences(ReadingLanguage.TURKISH), true)
        assertEquals(listOf(OnboardingWizardManager.KEIYOUSHI_INDEX), repository.urls)
        assertTrue(result.completed)
        assertTrue(result.suggestedPackages.isEmpty())
        assertEquals(result, store.state)
    }

    @Test fun `both languages suggests Turkish and English but no unrelated or adult sources`() = runTest {
        val result = OnboardingWizardManager(Repository(), Store()).complete(ReaderPreferences(ReadingLanguage.BOTH), true)
        assertEquals(listOf("en", "tr"), result.suggestedPackages)
    }

    private class Store : OnboardingStore {
        var state: OnboardingState? = null
        override suspend fun read() = state
        override suspend fun write(state: OnboardingState) { this.state = state }
    }

    private class Repository(val failRefresh: Boolean = false) : ExtensionRepository {
        val urls = mutableListOf<String>()
        var refreshed = false
        override suspend fun addRepository(indexUrl: String) { urls += indexUrl }
        override suspend fun removeRepository(indexUrl: String) { urls -= indexUrl }
        override suspend fun repositories() = urls.toList()
        override suspend fun refresh(): List<ExtensionDescriptor> {
            refreshed = true
            check(!failRefresh)
            return listOf(extension("tr"), extension("en"), extension("ja"), extension("tr-adult", "tr", true))
        }
        override suspend fun available() = emptyList<ExtensionDescriptor>()
        override suspend fun installed() = emptyList<InstalledExtension>()
        private fun extension(id: String, language: String = id, nsfw: Boolean = false) =
            ExtensionDescriptor(id, id, language, nsfw, "1", 1, "https://example.org/$id.apk", listOf(ExtensionSource(id, id, language)))
    }
}
