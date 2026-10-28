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
    private val auth = FakeAuth()
    private var resetCalls = 0
    private var resetFails = false
    private val reset = FactoryReset {
        resetCalls++
        if (resetFails) throw IOException("reset refused")
    }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }

    private fun model() = SettingsMaintenanceViewModel(services, settings, onboarding, reset, auth).also { models.put("settings", it) }

    @Test fun `factory reset only runs on explicit request and blocks duplicate mutations`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        assertEquals(0, resetCalls)
        model.factoryReset()
        model.factoryReset()
        model.clearCache()
        model.syncNow()
        model.setAutomaticSync(true)
        advanceUntilIdle()
        assertEquals(1, resetCalls)
        assertEquals(0, services.clearCalls)
        assertFalse(settings.read().automaticSync)
        assertTrue(model.state.value.resetBusy)
    }

    @Test fun `refused factory reset reports failure and permits retry`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        resetFails = true
        model.factoryReset()
        advanceUntilIdle()
        assertFalse(model.state.value.resetBusy)
        assertEquals(MaintenanceMessage.RESET_FAILED, model.state.value.message)
        resetFails = false
        model.factoryReset()
        advanceUntilIdle()
        assertEquals(2, resetCalls)
        assertTrue(model.state.value.resetBusy)
        assertNull(model.state.value.message)
    }

    @Test fun `factory reset waits until an active maintenance operation finishes`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        services.clearGate = CompletableDeferred()
        model.clearCache()
        model.factoryReset()
        advanceUntilIdle()
        assertEquals(0, resetCalls)
        services.clearGate?.complete(Unit)
        advanceUntilIdle()
        model.factoryReset()
        advanceUntilIdle()
        assertEquals(1, resetCalls)
    }

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
        auth.email.value = "reader@gmail.com"
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
        auth.email.value = "reader@gmail.com"
        services.result = SyncResult.Success(SyncPayload(deviceId = "test"), false, true, 5678L)
        val model = model()
        advanceUntilIdle()
        model.setAutomaticSync(true)
        advanceUntilIdle()
        assertTrue(settings.read().automaticSync)
        assertEquals(MaintenanceMessage.SYNC_SUCCESS, model.state.value.message)
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

    @Test fun `safety settings persist blacklist and clear explicit consent for minors`() = runTest(dispatcher) {
        val model = model()
        model.state.first { it.readerPreferences != null }
        val preferences = ReaderPreferences(ReadingLanguage.BOTH, true, false, setOf("ntr", "yuri"), setOf("murim"))
        model.setReaderPreferences(preferences)
        model.state.first { !it.preferenceBusy && it.readerPreferences?.excludedTags == preferences.excludedTags }
        assertEquals(preferences.copy(allowAdultContent = false), onboarding.value.preferences)
        assertEquals(onboarding.value.preferences, model.state.value.readerPreferences)
    }

    @Test fun `settings rerun preferences preserve adult opt in and blacklist`() = runTest(dispatcher) {
        val model = model()
        model.state.first { it.readerPreferences != null }
        val preferences = ReaderPreferences(ReadingLanguage.ENGLISH, true, true, setOf("ntr"))
        model.setReaderPreferences(preferences)
        model.state.first { !it.preferenceBusy && it.readerPreferences == preferences }
        model.setContentLanguage(ReadingLanguage.BOTH)
        model.state.first { !it.preferenceBusy && it.contentLanguage == ReadingLanguage.BOTH }
        assertEquals(preferences.copy(language = ReadingLanguage.BOTH), onboarding.value.preferences)
    }

    @Test fun `disconnected account gates sync and automatic opt in`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.setAutomaticSync(true)
        model.syncNow()
        advanceUntilIdle()
        assertFalse(settings.read().automaticSync)
        assertEquals(0, services.syncCalls)
        assertEquals(MaintenanceMessage.SIGN_IN_REQUIRED, model.state.value.message)
    }

    @Test fun `restored account displays email and disconnect disables automatic sync`() = runTest(dispatcher) {
        auth.email.value = "reader@gmail.com"
        settings.update { it.copy(automaticSync = true) }
        val model = model()
        advanceUntilIdle()
        assertEquals("reader@gmail.com", model.state.value.driveAccountEmail)
        model.disconnectDrive()
        advanceUntilIdle()
        assertNull(model.state.value.driveAccountEmail)
        assertTrue(model.state.value.driveSignInRequired)
        assertFalse(settings.read().automaticSync)
        assertFalse(model.state.value.driveAuthBusy)
        assertEquals(1, auth.disconnectCalls)
    }

    @Test fun `revoked consent gates sync and clears automatic preference`() = runTest(dispatcher) {
        auth.email.value = "reader@gmail.com"
        services.result = SyncResult.Success(SyncPayload(deviceId = "test"), false, true, 5678L)
        val model = model()
        advanceUntilIdle()
        model.setAutomaticSync(true)
        advanceUntilIdle()
        auth.email.value = null
        advanceUntilIdle()
        assertFalse(settings.read().automaticSync)
        assertNull(model.state.value.driveAccountEmail)
    }

    @Test fun `cancelled sign in is quiet and failed consent reports an error`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.completeDriveSignIn(null, cancelled = true)
        assertNull(model.state.value.message)
        model.completeDriveSignIn(null)
        assertEquals(MaintenanceMessage.AUTH_FAILED, model.state.value.message)
        assertNull(model.state.value.driveAccountEmail)
        assertFalse(model.state.value.driveAuthBusy)
    }

    @Test fun `successful consent publishes account and failed revocation stays disconnected`() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        auth.signInSucceeds = true
        model.completeDriveSignIn(null)
        advanceUntilIdle()
        assertEquals("reader@gmail.com", model.state.value.driveAccountEmail)
        auth.revokeSucceeds = false
        model.disconnectDrive()
        advanceUntilIdle()
        assertNull(model.state.value.driveAccountEmail)
        assertEquals(MaintenanceMessage.DISCONNECT_FAILED, model.state.value.message)
    }

    private class FakeAuth : DriveAccountAuth {
        override val email = MutableStateFlow<String?>(null)
        var disconnectCalls = 0
        var signInSucceeds = false
        var revokeSucceeds = true
        override fun signInIntent(): android.content.Intent = error("Not needed in JVM tests")
        override fun completeSignIn(data: android.content.Intent?): Boolean {
            if (signInSucceeds) email.value = "reader@gmail.com"
            return signInSucceeds
        }
        override fun disconnect(onComplete: (Boolean) -> Unit) {
            disconnectCalls++
            email.value = null
            onComplete(revokeSucceeds)
        }
        override fun clearConnection() { email.value = null }
    }

    private class FakeServices : SettingsMaintenanceServices {
        override val lastSyncedAt = MutableStateFlow<Long?>(null)
        var bytes = 4096L
        var clearCalls = 0
        var clearFails = false
        var clearGate: CompletableDeferred<Unit>? = null
        var result: SyncResult = SyncResult.NotSignedIn
        var syncCalls = 0
        override suspend fun cacheSize() = bytes
        override suspend fun clearCache() {
            clearCalls++
            clearGate?.await()
            if (clearFails) throw IOException("disk unavailable")
            bytes = 0L
        }
        override suspend fun sync(): SyncResult { syncCalls++; return result }
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
