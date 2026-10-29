package com.yomira.domain.sync

import com.yomira.data.sync.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SyncProjectionWriterTest {
    private val payload = SyncPayload(deviceId = "backup", library = listOf(LibrarySyncEntry("series")),
        settings = UserSettingsSync(), extensions = listOf(InstalledExtensionSyncEntry("extension", 1)))

    @Test fun libraryRestoreNeverCallsExtensionMaterializationEvenForEmptyManifestList() = runTest {
        for (input in listOf(payload, payload.copy(extensions = emptyList()))) {
            var stored: SyncPayload? = null
            writeSyncProjection(input, true, { stored = it }, { error("Must not touch extension store") })
            val restored = checkNotNull(stored)
            assertEquals(input.library, restored.library)
            assertEquals(input.readingHistory, restored.readingHistory)
            assertNull(restored.settings)
            assertTrue(restored.extensions.isEmpty())
        }
    }
    @Test fun normalSyncKeepsOriginalDatabaseThenExtensionContract() = runTest {
        val order = mutableListOf<String>()
        writeSyncProjection(payload, false, {
            assertEquals(payload, it); order += "database"
        }, {
            assertEquals(payload.extensions, it); order += "extensions"
        })
        assertEquals(listOf("database", "extensions"), order)
    }
    @Test fun failedDatabaseWriteDoesNotMaterializeExtensions() = runTest {
        try {
            writeSyncProjection(payload, false, { throw IllegalStateException("disk") }, { fail("Wrong order") })
            fail("Failure must propagate")
        } catch (expected: IllegalStateException) { assertEquals("disk", expected.message) }
    }
}
