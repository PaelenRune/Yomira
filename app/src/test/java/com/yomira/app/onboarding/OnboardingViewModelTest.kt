package com.yomira.app.onboarding

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.InstalledExtension
import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun newInstallRequiresSetupAndAlwaysRegistersBundledRepositories() = runBlocking {
        val store = MemoryStore()
        val added = mutableListOf<String>()
        val model = model(store, added = added)
        assertFalse(model.await { !it.loading }.completed)
        model.complete("tr", false)
        model.await { it.completed }
        assertEquals(ReaderPreferences(ReadingLanguage.TURKISH), store.saved?.preferences)
        assertEquals(OnboardingWizardManager.TURKISH_INDEX, store.saved?.repositoryUrl)
        assertEquals(OnboardingWizardManager.ONBOARDING_INDEXES, added)
    }

    @Test fun completedSetupBypassesWizardAfterRestart() = runBlocking {
        val saved = OnboardingState(ReaderPreferences(ReadingLanguage.ENGLISH, true), null, emptyList(), true)
        val model = model(MemoryStore(saved))
        val loaded = model.await { !it.loading }
        assertTrue(loaded.completed)
        assertEquals("en", loaded.languageCode)
    }

    @Test fun bothReadingLanguagesAreSavedAndRestored() = runBlocking {
        val store = MemoryStore()
        val first = model(store)
        first.await { !it.loading }
        first.complete("both", true)
        assertEquals("both", first.await { it.completed }.languageCode)
        assertEquals(ReaderPreferences(ReadingLanguage.BOTH), store.saved?.preferences)
        assertEquals("both", model(store).await { !it.loading }.languageCode)
    }

    @Test fun failedRefreshStillCompletesWithBundledRepositoriesRegistered() = runBlocking {
        val store = MemoryStore()
        val added = mutableListOf<String>()
        val model = model(store, failNetwork = true, added = added)
        model.await { !it.loading }
        model.complete("en", true)
        val completed = model.await { it.completed || it.error != null }
        assertTrue(completed.completed)
        assertFalse(completed.saving)
        assertEquals(null, completed.error)
        assertEquals(OnboardingWizardManager.ONBOARDING_INDEXES, added)
        assertTrue(store.saved!!.suggestedPackages.isEmpty())
        assertFalse(store.saved!!.preferences.allowAdultContent)
        assertEquals(ReadingLanguage.ENGLISH, store.saved?.preferences?.language)
    }

    @Test fun unreadableStorageNeverFallsThroughToHome() = runBlocking {
        val store = object : OnboardingStore {
            override suspend fun read(): OnboardingState? = throw IOException("unreadable")
            override suspend fun write(state: OnboardingState) = Unit
        }
        val model = model(store)
        val failed = model.await { it.error != null }
        assertTrue(failed.loading)
        assertFalse(failed.completed)
    }

    private fun model(
        store: OnboardingStore,
        failNetwork: Boolean = false,
        added: MutableList<String> = mutableListOf(),
    ): OnboardingViewModel {
        val extensions = object : ExtensionRepository {
            override suspend fun addRepository(indexUrl: String) { added += indexUrl }
            override suspend fun removeRepository(indexUrl: String) = Unit
            override suspend fun repositories(): List<String> = emptyList()
            override suspend fun refresh(): List<ExtensionDescriptor> {
                if (failNetwork) throw IOException("offline")
                return emptyList()
            }
            override suspend fun available(): List<ExtensionDescriptor> = emptyList()
            override suspend fun installed(): List<InstalledExtension> = emptyList()
        }
        return OnboardingViewModel(OnboardingWizardManager(extensions, store))
    }

    private suspend fun OnboardingViewModel.await(predicate: (OnboardingUiState) -> Boolean) =
        withTimeout(5_000) { state.first(predicate) }

    private class MemoryStore(@Volatile var saved: OnboardingState? = null) : OnboardingStore {
        override suspend fun read(): OnboardingState? = saved
        override suspend fun write(state: OnboardingState) { saved = state }
    }
}
