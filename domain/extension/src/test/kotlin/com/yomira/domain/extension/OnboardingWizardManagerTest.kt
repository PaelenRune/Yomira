package com.yomira.domain.extension

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OnboardingWizardManagerTest {
    @Test fun `skip repo still persists language adult policy and completion`() = runTest {
        val store = Store()
        val repository = Repository()
        val preferences = ReaderPreferences(ReadingLanguage.ENGLISH, true)
        val result = OnboardingWizardManager(repository, store).complete(preferences, false)
        assertEquals(preferences, result.preferences)
        assertTrue(result.completed)
        assertNull(result.repositoryUrl)
        assertTrue(repository.urls.isEmpty())
        assertEquals(result, store.state)
    }

    @Test fun `one click adds official repo and prepares only allowed sources`() = runTest {
        val store = Store()
        val repository = Repository()
        val result = OnboardingWizardManager(repository, store).complete(ReaderPreferences(ReadingLanguage.TURKISH), true)
        assertEquals(listOf(OnboardingWizardManager.KEIYOUSHI_INDEX), repository.urls)
        assertEquals(listOf("tr"), result.suggestedPackages)
    }

    @Test fun `failed refresh does not falsely mark onboarding completed`() = runTest {
        val store = Store()
        val manager = OnboardingWizardManager(Repository(failRefresh = true), store)
        try {
            manager.complete(ReaderPreferences(ReadingLanguage.TURKISH), true)
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertNull(store.state)
    }

    private class Store : OnboardingStore {
        var state: OnboardingState? = null
        override suspend fun read() = state
        override suspend fun write(state: OnboardingState) { this.state = state }
    }

    private class Repository(val failRefresh: Boolean = false) : ExtensionRepository {
        val urls = mutableListOf<String>()
        override suspend fun addRepository(indexUrl: String) { urls += indexUrl }
        override suspend fun repositories() = urls.toList()
        override suspend fun refresh(): List<ExtensionDescriptor> {
            check(!failRefresh)
            return listOf(extension("tr"), extension("en"), extension("tr-adult", "tr", true))
        }
        override suspend fun available() = emptyList<ExtensionDescriptor>()
        override suspend fun installed() = emptyList<InstalledExtension>()
        private fun extension(id: String, language: String = id, nsfw: Boolean = false) =
            ExtensionDescriptor(id, id, language, nsfw, "1", 1, "https://example.org/$id.apk", listOf(ExtensionSource(id, id, language)))
    }
}
