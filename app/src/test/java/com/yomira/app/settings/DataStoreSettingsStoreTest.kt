package com.yomira.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yomira.core.model.ReadingMode
import com.yomira.feature.settings.AppSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
        val hidden = AppSettings(
            vaultVisible = false,
            requireDeviceLock = false,
            lockOnBackground = false,
            readingMode = ReadingMode.WEBTOON,
        )
        try { store(file, firstScope).updateWithDiskRetry { hidden } } finally { firstScope.cancel(); firstJob.join() }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertEquals(hidden, store(file, secondScope).read())
            // Toggling one switch must never disturb the others.
            store(file, secondScope).updateWithDiskRetry { it.copy(vaultVisible = true) }
            assertEquals(hidden.copy(vaultVisible = true), store(file, secondScope).read())
        } finally { secondScope.cancel() }
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
            val store = store(File(temporary.root, "atomic.preferences_pb"), scope)
            store.updateWithDiskRetry { it.copy(requireDeviceLock = false) }
            store.updateWithDiskRetry { current ->
                assertEquals(false, current.requireDeviceLock)
                current.copy(lockOnBackground = false)
            }
            val settings = store.read()
            assertEquals(false, settings.requireDeviceLock)
            assertEquals(false, settings.lockOnBackground)
            assertEquals(true, settings.vaultVisible)
        } finally { scope.cancel() }
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
        repeat(3) { attempt ->
            try {
                update(transform)
                return
            } catch (error: IOException) {
                if (attempt == 2) throw error
                withContext(Dispatchers.IO) { delay(150) }
            }
        }
    }
}
