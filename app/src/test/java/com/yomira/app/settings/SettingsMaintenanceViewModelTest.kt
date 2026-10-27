package com.yomira.app.settings

import androidx.lifecycle.ViewModelStore
import com.yomira.data.sync.SyncPayload
import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import com.yomira.domain.sync.SyncResult
import com.yomira.feature.settings.AppSettings
import com.yomira.feature.settings.MaintenanceMessage
import com.yomira.feature.settings.SettingsStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsMaintenanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val services = FakeServices()
    private val settings = MemorySettings()
    private val onboarding = MemoryOnboarding()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun model() = SettingsMaintenanceViewModel(services, settings, onboarding).also { models.put("settings", it) }

    @Test fun `cache size is refreshed after confirmed clear and duplicate operations are ignored`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        assertEquals(4096L, model.state.value.cacheBytes)
        services.clearGate = CompletableDeferred()
        model.clearCache()
        model.clearCache()
        advanceUntilIdle()
        assertTrue(model.state.value.cacheBusy)
        assertEquals(1, services.clearCalls)
        services.clearGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(0L, model.state.value.cacheBytes)
        assertEquals(MaintenanceMessage.CACHE_CLEARED, model.state.value.message)
        assertFalse(model.state.value.cacheBusy)
        model.consumeMessage()
        assertNull(model.state.value.message)
    }

    @Test fun `cache failure retains the measured size and allows retry`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        services.clearFails = true
        model.clearCache()
        advanceUntilIdle()
        assertEquals(MaintenanceMessage.CACHE_FAILED, model.state.value.message)
        assertEquals(4096L, model.state.value.cacheBytes)
        assertFalse(model.state.value.cacheBusy)
        services.clearFails = false
        model.clearCache()
        advanceUntilIdle()
        assertEquals(MaintenanceMessage.CACHE_CLEARED, model.state.value.message)
    }

    @Test fun `unconfigured Drive never reports a successful sync`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.syncNow()
        advanceUntilIdle()
        assertEquals(MaintenanceMessage.SIGN_IN_REQUIRED, model.state.value.message)
        assertTrue(model.state.value.driveSignInRequired)
        assertNull(model.state.value.lastSyncedAt)
        assertFalse(model.state.value.syncBusy)
    }

    @Test fun `manual sync exposes transport failure and observes persisted background timestamps`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        services.result = SyncResult.Failure("offline", retryable = true)
        model.syncNow()
        advanceUntilIdle()
        assertEquals(MaintenanceMessage.SYNC_FAILED, model.state.value.message)
        services.lastSyncedAt.value = 1234L
        advanceUntilIdle()
        assertEquals(1234L, model.state.value.lastSyncedAt)
        services.result = SyncResult.Success(SyncPayload(deviceId = "test"), true, true, 5678L)
        model.syncNow()
        advanceUntilIdle()
        assertEquals(5678L, model.state.value.lastSyncedAt)
        assertEquals(MaintenanceMessage.SYNC_SUCCESS, model.state.value.message)
        assertFalse(model.state.value.driveSignInRequired)
    }

    @Test fun `automatic sync opt in is persisted and attempts an immediate sync`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.setAutomaticSync(true)
        advanceUntilIdle()
        assertTrue(settings.read().automaticSync)
        assertEquals(MaintenanceMessage.SIGN_IN_REQUIRED, model.state.value.message)
        model.setAutomaticSync(false)
        advanceUntilIdle()
        assertFalse(settings.read().automaticSync)
    }

    @Test fun `content language updates onboarding without changing consent or installed suggestions`() = runTest(dispatcher) {
        val model = model()
        model.state.first { it.contentLanguage != null }
        val before = onboarding.value
        model.setContentLanguage(ReadingLanguage.ENGLISH)
        model.state.first { it.contentLanguage == ReadingLanguage.ENGLISH && !it.preferenceBusy }
        assertEquals(before.copy(preferences = before.preferences.copy(language = ReadingLanguage.ENGLISH)), onboarding.value)
    }

    private class FakeServices : SettingsMaintenanceServices {
        override val lastSyncedAt = MutableStateFlow<Long?>(null)
        var bytes = 4096L
        var clearCalls = 0
        var clearFails = false
        var clearGate: CompletableDeferred<Unit>? = null
        var result: SyncResult = SyncResult.NotSignedIn
        override suspend fun cacheSize() = bytes
        override suspend fun clearCache() {
            clearCalls++
            clearGate?.await()
            if (clearFails) throw IOException("disk unavailable")
            bytes = 0L
        }
        override suspend fun sync() = result
    }

    private class MemorySettings : SettingsStore {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun read() = settings.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }

    private class MemoryOnboarding : OnboardingStore {
        var value = OnboardingState(ReaderPreferences(ReadingLanguage.BOTH, true), "https://example.org/index.json", listOf("source"), true)
        override suspend fun read() = value
        override suspend fun write(state: OnboardingState) { value = state }
    }
}
