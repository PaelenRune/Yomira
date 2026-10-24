package com.yomira.app.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yomira.core.model.ReadingMode
import com.yomira.feature.settings.AppSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
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
        try { store(file, firstScope).update { hidden } } finally { firstScope.cancel(); firstJob.join() }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            assertEquals(hidden, store(file, secondScope).read())
            // Toggling one switch must never disturb the others.
            store(file, secondScope).update { it.copy(vaultVisible = true) }
            assertEquals(hidden.copy(vaultVisible = true), store(file, secondScope).read())
        } finally { secondScope.cancel() }
    }

    @Test fun corruptValuesFallBackToDefaultsInsteadOfCrashing() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = store(File(temporary.root, "corrupt.preferences_pb"), scope)
            store.update { it.copy(readingMode = ReadingMode.WESTERN_LTR) }
            store.update { AppSettings(readingMode = ReadingMode.MANGA_RTL) }
            assertEquals(AppSettings(), store.read())
        } finally { scope.cancel() }
    }

    @Test fun updateTransformSeesThePersistedStateAtomically() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = store(File(temporary.root, "atomic.preferences_pb"), scope)
            store.update { it.copy(requireDeviceLock = false) }
            store.update { current ->
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
}
