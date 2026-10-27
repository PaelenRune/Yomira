package com.yomira.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yomira.app.onboarding.DataStoreOnboardingStore
import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import com.yomira.feature.settings.AppSettings
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class FactoryResetTest {
    @Test fun `reset empties both preference stores including unknown keys and restores first use`() = runTest {
        // Exercise production store wrappers and Preferences.edit/clear with deterministic
        // storage. Android FileStorage's rename-over-existing fails on a Windows JVM.
        val settingsData = MemoryDataStore()
        val onboardingData = MemoryDataStore()
        val settings = DataStoreSettingsStore(settingsData)
        val onboarding = DataStoreOnboardingStore(onboardingData)
        settings.update { it.copy(vaultVisible = true, automaticSync = true, bundledRepositoriesSeeded = true) }
        onboarding.write(OnboardingState(ReaderPreferences(ReadingLanguage.TURKISH, true, true), "https://example.org", listOf("extension"), true))
        settingsData.edit { it[stringPreferencesKey("future-setting")] = "old" }
        val operations = mutableListOf<String>()
        CompleteFactoryReset(
            clearDatabase = { operations += "database" },
            clearSettings = { settingsData.edit { it.clear() }; Unit },
            clearOnboarding = { onboardingData.edit { it.clear() }; Unit },
            clearCaches = { operations += "caches" },
            clearApplicationUserData = { operations += "android"; true },
        ).reset()
        assertEquals(listOf("caches", "database", "android"), operations)
        assertTrue(settingsData.data.first().asMap().isEmpty())
        assertTrue(onboardingData.data.first().asMap().isEmpty())
        assertEquals(AppSettings(), settings.read())
        assertNull(onboarding.read())
    }

    @Test fun `owner failure does not prevent remaining owners or authoritative Android wipe`() = runTest {
        val operations = mutableListOf<String>()
        CompleteFactoryReset(
            clearDatabase = { operations += "database" },
            clearSettings = { operations += "settings" },
            clearOnboarding = { operations += "onboarding" },
            clearCaches = { throw IOException("cache unavailable") },
            clearApplicationUserData = { operations += "android"; true },
        ).reset()
        assertEquals(listOf("database", "settings", "onboarding", "android"), operations)
    }

    @Test fun `Android refusal is never reported as success`() = runTest {
        val reset = CompleteFactoryReset({}, {}, {}, { throw IOException("disk") }, { false })
        try {
            reset.reset()
            fail("Expected reset refusal")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("Android refused"))
        }
    }

    private class MemoryDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
}
