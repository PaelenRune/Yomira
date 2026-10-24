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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun newInstallRequiresSetupAndSkipSavesSafeDefaults() = runBlocking {
        val store = MemoryStore()
        val model = model(store)
        assertFalse(model.await { !it.loading }.completed)
        model.complete("tr", false, false)
        val completed = model.await { it.completed }
        assertFalse(completed.adultEnabled)
        assertEquals(ReaderPreferences(ReadingLanguage.TURKISH), store.saved?.preferences)
        assertEquals(null, store.saved?.repositoryUrl)
    }

    @Test fun completedSetupBypassesWizardAfterRestart() = runBlocking {
        val saved = OnboardingState(ReaderPreferences(ReadingLanguage.ENGLISH, true), null, emptyList(), true)
        val model = model(MemoryStore(saved))
        val loaded = model.await { !it.loading }
        assertTrue(loaded.completed)
        assertTrue(loaded.adultEnabled)
    }

    @Test fun failedRepositoryRefreshDoesNotCompleteAndCanRecoverWithoutRepository() = runBlocking {
        val store = MemoryStore()
        val model = model(store, failNetwork = true)
        model.await { !it.loading }
        model.complete("en", true, true)
        val failed = model.await { it.error != null }
        assertFalse(failed.completed)
        assertFalse(failed.saving)
        assertNotNull(failed.error)
        assertEquals(null, store.saved)
        model.complete("en", true, false)
        assertTrue(model.await { it.completed }.adultEnabled)
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

    private fun model(store: OnboardingStore, failNetwork: Boolean = false): OnboardingViewModel {
        val extensions = object : ExtensionRepository {
            override suspend fun addRepository(indexUrl: String) = Unit
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
