package com.yomira.domain.extension

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OnboardingWizardManagerTest {
    @Test fun `bundled repositories are registered even when the legacy opt-in is false`() = runTest {
        val store = Store()
        val repository = Repository()
        val preferences = ReaderPreferences(ReadingLanguage.ENGLISH, true)
        val result = OnboardingWizardManager(repository, store).complete(preferences, false)
        assertEquals(preferences, result.preferences)
        assertTrue(result.completed)
        assertEquals(OnboardingWizardManager.WESTERN_INDEX, result.repositoryUrl)
        assertEquals(OnboardingWizardManager.ONBOARDING_INDEXES, repository.urls)
        assertEquals(result, store.state)
    }

    @Test fun `bundled repositories are registered and only allowed sources are suggested`() = runTest {
        val store = Store()
        val repository = Repository()
        val result = OnboardingWizardManager(repository, store).complete(ReaderPreferences(ReadingLanguage.TURKISH), true)
        assertEquals(OnboardingWizardManager.ONBOARDING_INDEXES, repository.urls)
        assertEquals(listOf("tr"), result.suggestedPackages)
    }

    @Test fun `failed refresh still completes with bundled repositories registered`() = runTest {
        val store = Store()
        val repository = Repository(failRefresh = true)
        val result = OnboardingWizardManager(repository, store).complete(
            ReaderPreferences(ReadingLanguage.TURKISH),
            true,
        )
        assertEquals(OnboardingWizardManager.ONBOARDING_INDEXES, repository.urls)
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
        override suspend fun addRepository(indexUrl: String) { urls += indexUrl }
        override suspend fun removeRepository(indexUrl: String) { urls -= indexUrl }
        override suspend fun repositories() = urls.toList()
        override suspend fun refresh(): List<ExtensionDescriptor> {
            check(!failRefresh)
            return listOf(extension("tr"), extension("en"), extension("ja"), extension("tr-adult", "tr", true))
        }
        override suspend fun available() = emptyList<ExtensionDescriptor>()
        override suspend fun installed() = emptyList<InstalledExtension>()
        private fun extension(id: String, language: String = id, nsfw: Boolean = false) =
            ExtensionDescriptor(id, id, language, nsfw, "1", 1, "https://example.org/$id.apk", listOf(ExtensionSource(id, id, language)))
    }
}
