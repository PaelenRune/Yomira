package com.yomira.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yomira.data.extension.ExtensionConsent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreExtensionConsentStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val fingerprint = "54da2d6e1de01e30847e5a1c855221cb8a72fff2be111ea695488c855f0b1ae3"

    /**
     * Android FileStorage's rename-over-existing fails on a Windows JVM (see
     * FactoryResetTest), so multi-write semantics run against an in-memory DataStore
     * while cross-process persistence keeps the proven single-write real-file pattern.
     */
    private class MemoryDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    @Test fun `granted consents persist across store recreation`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope, produceFile = { File(temporary.root, "consents.preferences_pb") },
            )
            val record = ExtensionConsent("eu.kanade.test", fingerprint, 1_700_000_000_000L, "tr")
            DataStoreExtensionConsentStore(dataStore).grant(record)

            // A recreated wrapper (new process) reads the same inventory.
            assertEquals(listOf(record), DataStoreExtensionConsentStore(dataStore).all())
        } finally { scope.cancel() }
    }

    @Test fun `revoke clears the inventory entry for its package`() = runTest {
        val store = DataStoreExtensionConsentStore(MemoryDataStore())
        store.grant(ExtensionConsent("eu.kanade.test", fingerprint, 1_700_000_000_000L, "tr"))
        store.grant(ExtensionConsent("eu.kanade.other", null, 1_700_000_000_001L, "en"))
        assertEquals(setOf("eu.kanade.test", "eu.kanade.other"), store.all().map { it.packageName }.toSet())

        store.revoke("eu.kanade.test")
        assertEquals(listOf("eu.kanade.other"), store.all().map { it.packageName })
    }

    @Test fun `regranting a package replaces its record instead of duplicating`() = runTest {
        val store = DataStoreExtensionConsentStore(MemoryDataStore())
        store.grant(ExtensionConsent("eu.kanade.test", null, 1L, "tr"))
        store.grant(ExtensionConsent("eu.kanade.test", fingerprint, 2L, "en"))
        val record = store.all().single()
        assertEquals(fingerprint, record.publisherFingerprint)
        assertEquals("en", record.language)
    }

    @Test fun `corrupted inventory falls back to empty instead of crashing`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope, produceFile = { File(temporary.root, "corrupt.preferences_pb") },
            )
            dataStore.edit { it[stringPreferencesKey("extensionFirstUseConsents")] = "{not json" }
            assertTrue(DataStoreExtensionConsentStore(dataStore).all().isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun `an empty inventory reads as no consents`() = runTest {
        val store = DataStoreExtensionConsentStore(MemoryDataStore())
        assertTrue(store.all().isEmpty())
        store.grant(ExtensionConsent("eu.kanade.test", fingerprint, 5L, "en"))
        assertEquals(1, store.all().size)
    }
}
