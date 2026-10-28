package com.yomira.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yomira.core.model.ReadingMode
import com.yomira.feature.settings.AppSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun freshInstallYieldsTheSecureDefaults() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = store(File(temporary.root, "fresh.preferences_pb"), scope)
            assertEquals(AppSettings(), store.read())
        } finally { scope.cancel() }
    }

    @Test fun settingsSurviveStoreRecreation() = runTest {
        val file = File(temporary.root, "persisted.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val raw = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val hidden = AppSettings(
                vaultVisible = false,
                requireDeviceLock = false,
                lockOnBackground = false,
                readingMode = ReadingMode.WEBTOON,
                automaticSync = true,
                repositoriesUserManaged = true,
            )
            val firstStore = DataStoreSettingsStore(raw)
            firstStore.update { hidden }

            // Recreated wrapper instance reads the same persisted data
            val recreatedStore = DataStoreSettingsStore(raw)
            assertEquals(hidden, recreatedStore.read())
        } finally { scope.cancel() }
    }

    @Test fun corruptValuesFallBackToDefaultsInsteadOfCrashing() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val file = File(temporary.root, "corrupt.preferences_pb")
            // Write raw garbage under the readingMode key; the typed store must
            // fall back instead of crashing or propagating the unknown value.
            val raw = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            raw.edit { values -> values[stringPreferencesKey("readingMode")] = "NOT_A_REAL_MODE" }
            assertEquals(ReadingMode.MANGA_RTL, DataStoreSettingsStore(raw).read().readingMode)
            assertEquals(AppSettings(), DataStoreSettingsStore(raw).read())
        } finally { scope.cancel() }
    }

    @Test fun updateTransformSeesThePersistedStateAtomically() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val file = File(temporary.root, "atomic.preferences_pb")
            val raw = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val store = DataStoreSettingsStore(raw)
            store.update { current ->
                current.copy(requireDeviceLock = false, lockOnBackground = false)
            }
            val settings = store.read()
            assertEquals(false, settings.requireDeviceLock)
            assertEquals(false, settings.lockOnBackground)
            assertEquals(false, settings.vaultVisible)
        } finally { scope.cancel() }
    }

    /**
     * v8.2.0 Vault NSFW first-use consent roundtrip.
     *
     * Android FileStorage's rename-over-existing fails on a Windows JVM (see
     * FactoryResetTest and DataStoreExtensionConsentStoreTest), so — following
     * the repo convention — cross-instance persistence keeps the proven
     * single-write real-file pattern, while the full decision-change chain
     * (accept → decline → unrelated writes → accept again) runs against an
     * in-memory DataStore that exercises the same update pipeline.
     */
    @Test fun vaultNsfwConsentRoundTripsThroughTheDataStore() = runTest {
        // Fresh install: no decision yet, so the first-use prompt is shown.
        withSettingsStore(File(temporary.root, "vault_fresh.preferences_pb")) {
            assertEquals(null, it.read().vaultNsfwConsent)
        }

        // "Yes, show": the accept decision persists and survives store recreation.
        val accepted = File(temporary.root, "vault_accepted.preferences_pb")
        withSettingsStore(accepted) {
            it.updateWithDiskRetry { current -> current.copy(vaultNsfwConsent = true) }
        }
        withSettingsStore(accepted) {
            assertEquals(true, it.read().vaultNsfwConsent)
        }

        // "No, keep hidden": declining is equally durable, and the decision is
        // independent of the Vault tab staying visible.
        val declined = File(temporary.root, "vault_declined.preferences_pb")
        withSettingsStore(declined) {
            it.updateWithDiskRetry { current -> current.copy(vaultNsfwConsent = false, vaultVisible = true) }
        }
        withSettingsStore(declined) {
            val state = it.read()
            assertEquals(false, state.vaultNsfwConsent)
            assertEquals(true, state.vaultVisible)
        }
    }

    /** The full decision-change chain through the real update pipeline. */
    @Test fun vaultNsfwConsentDecisionChangesThroughTheUpdatePipeline() = runTest {
        val store = DataStoreSettingsStore(MemoryDataStore())

        // Fresh state: no decision yet.
        assertEquals(null, store.read().vaultNsfwConsent)

        // Accepting persists…
        store.update { it.copy(vaultNsfwConsent = true) }
        assertEquals(true, store.read().vaultNsfwConsent)

        // …declining overwrites it…
        store.update { it.copy(vaultNsfwConsent = false) }
        assertEquals(false, store.read().vaultNsfwConsent)

        // …unrelated writes (toggling the Vault tab) never touch the decision…
        store.update { it.copy(vaultVisible = true) }
        val midState = store.read()
        assertEquals(false, midState.vaultNsfwConsent)
        assertEquals(true, midState.vaultVisible)

        // …and changing the mind back to "show" works too.
        store.update { it.copy(vaultNsfwConsent = true) }
        val endState = store.read()
        assertEquals(true, endState.vaultNsfwConsent)
        assertEquals(true, endState.vaultVisible)
    }

    /** One DataStore instance per phase, fully torn down before the next one. */
    private suspend fun withSettingsStore(file: File, block: suspend (DataStoreSettingsStore) -> Unit) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        try {
            block(DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })))
        } finally {
            scope.cancel()
            job.join()
            kotlinx.coroutines.delay(50)
        }
    }

    /** Windows-safe in-memory DataStore for multi-write semantics (repo convention). */
    private class MemoryDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(data.value).also { data.value = it } }
    }

    private fun store(file: File, scope: CoroutineScope) = DataStoreSettingsStore(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )

    /**
     * DataStore persists through temp-file + rename. On a busy Windows machine
     * (antivirus or a lingering read handle) that rename can fail transiently
     * even though nothing else touches these unique temp files; a failed write
     * leaves the previous state intact, so retrying is safe and deterministic.
     */
    private suspend fun com.yomira.feature.settings.SettingsStore.updateWithDiskRetry(
        transform: (AppSettings) -> AppSettings,
    ) {
        repeat(5) { attempt ->
            try {
                update(transform)
                return
            } catch (error: Exception) {
                if (attempt == 4) throw error
                System.gc()
                withContext(Dispatchers.IO) { delay(200) }
            }
        }
    }
}
