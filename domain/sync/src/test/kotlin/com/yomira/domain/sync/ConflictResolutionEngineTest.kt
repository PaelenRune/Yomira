package com.yomira.domain.sync

import com.yomira.data.sync.LibrarySyncEntry
import com.yomira.data.sync.ReadingProgressSyncEntry
import com.yomira.data.sync.SyncPayload
import com.yomira.data.sync.UserSettingsSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionEngineTest {
    private val engine = ConflictResolutionEngine()

    @Test
    fun `library is unioned and keeps the newest category`() {
        val local = payload(
            library = listOf(LibrarySyncEntry("m1", true, "Favourites", 100)),
        )
        val remote = payload(
            deviceId = "device-b",
            library = listOf(
                LibrarySyncEntry("m1", false, "Reading", 200),
                LibrarySyncEntry("m2", true, "Plan to read", 300),
            ),
        )

        val merged = engine.merge(local, remote, mergedAt = 1_000)

        assertEquals(
            listOf(
                LibrarySyncEntry("m1", true, "Reading", 200),
                LibrarySyncEntry("m2", true, "Plan to read", 300),
            ),
            merged.library,
        )
    }

    @Test
    fun `library union never drops a title known by only one device`() {
        val merged = engine.merge(
            payload(library = listOf(LibrarySyncEntry("local-only", true))),
            payload(deviceId = "b", library = listOf(LibrarySyncEntry("remote-only", true))),
            mergedAt = 1_000,
        )

        assertEquals(listOf("local-only", "remote-only"), merged.library.map { it.mangaId })
        assertTrue(merged.library.all { it.inLibrary })
    }

    @Test
    fun `furthest chapter wins even when the other device has a newer timestamp`() {
        val merged = engine.merge(
            payload(progress = listOf(ReadingProgressSyncEntry("m", 10.0, 2, 500))),
            payload(
                deviceId = "b",
                progress = listOf(ReadingProgressSyncEntry("m", 9.0, 80, 9_999)),
            ),
            mergedAt = 1_000,
        )

        assertEquals(
            ReadingProgressSyncEntry("m", 10.0, 2, 500),
            merged.readingProgress.single(),
        )
    }

    @Test
    fun `furthest page wins inside the same chapter`() {
        val merged = engine.merge(
            payload(progress = listOf(ReadingProgressSyncEntry("m", 5.0, 10, 400))),
            payload(
                deviceId = "b",
                progress = listOf(ReadingProgressSyncEntry("m", 5.0, 12, 100)),
            ),
            mergedAt = 1_000,
        )

        assertEquals(
            ReadingProgressSyncEntry("m", 5.0, 12, 100),
            merged.readingProgress.single(),
        )
    }

    @Test
    fun `exact progress tie is broken by lastReadAt`() {
        val merged = engine.merge(
            payload(progress = listOf(ReadingProgressSyncEntry("m", 5.0, 12, 100))),
            payload(
                deviceId = "b",
                progress = listOf(ReadingProgressSyncEntry("m", 5.0, 12, 200)),
            ),
            mergedAt = 1_000,
        )

        assertEquals(200L, merged.readingProgress.single().lastReadAt)
    }

    @Test
    fun `newest user settings snapshot wins as a whole`() {
        val local = payload(
            settings = UserSettingsSync(gridColumns = 2, adultEnabled = false, theme = "oled", updatedAt = 400),
        )
        val remote = payload(
            deviceId = "b",
            settings = UserSettingsSync(gridColumns = 4, adultEnabled = true, theme = "dark", updatedAt = 500),
        )

        val merged = engine.merge(local, remote, mergedAt = 1_000)

        assertEquals(4, merged.settings?.gridColumns)
        assertTrue(merged.settings?.adultEnabled == true)
        assertEquals("dark", merged.settings?.theme)
    }

    @Test
    fun `local settings win when timestamps are equal`() {
        val merged = engine.merge(
            payload(settings = UserSettingsSync(gridColumns = 2, updatedAt = 500)),
            payload(
                deviceId = "b",
                settings = UserSettingsSync(gridColumns = 4, updatedAt = 500),
            ),
            mergedAt = 1_000,
        )

        assertEquals(2, merged.settings?.gridColumns)
    }

    @Test
    fun `missing remote keeps local content and advances the revision`() {
        val local = payload(
            library = listOf(LibrarySyncEntry("m", true)),
            progress = listOf(ReadingProgressSyncEntry("m", 3.0, 1, 10)),
            settings = UserSettingsSync(gridColumns = 3, updatedAt = 10),
        )

        val merged = engine.merge(local, remote = null, mergedAt = 5_000)

        assertEquals(local.library, merged.library)
        assertEquals(local.readingProgress, merged.readingProgress)
        assertEquals(local.settings, merged.settings)
        assertEquals(local.version + 1, merged.version)
        assertEquals(5_000L, merged.generatedAt)
        assertEquals(local.deviceId, merged.deviceId)
    }

    @Test
    fun `merge increments the highest revision and keeps the local device identity`() {
        val merged = engine.merge(
            payload(version = 7),
            payload(deviceId = "b", version = 12),
            mergedAt = 1_000,
        )

        assertEquals(13L, merged.version)
        assertEquals("device-a", merged.deviceId)
    }

    @Test
    fun `merged collections are sorted for deterministic uploads`() {
        val merged = engine.merge(
            payload(
                library = listOf(
                    LibrarySyncEntry("z", true),
                    LibrarySyncEntry("a", true),
                ),
                progress = listOf(
                    ReadingProgressSyncEntry("z", 1.0, 0, 1),
                    ReadingProgressSyncEntry("a", 1.0, 0, 1),
                ),
            ),
            remote = null,
            mergedAt = 1_000,
        )

        assertEquals(listOf("a", "z"), merged.library.map { it.mangaId })
        assertEquals(listOf("a", "z"), merged.readingProgress.map { it.mangaId })
    }

    @Test
    fun `settings merge handles a missing side`() {
        val settings = UserSettingsSync(gridColumns = 4, updatedAt = 10)
        assertNull(engine.merge(payload(settings = null), remote = null, mergedAt = 1).settings)
        assertEquals(
            settings,
            engine.merge(
                payload(settings = null),
                payload(deviceId = "b", settings = settings),
                mergedAt = 1,
            ).settings,
        )
        assertEquals(
            settings,
            engine.merge(
                payload(settings = settings),
                payload(deviceId = "b", settings = null),
                mergedAt = 1,
            ).settings,
        )
    }

    private fun payload(
        deviceId: String = "device-a",
        version: Long = 1,
        library: List<LibrarySyncEntry> = emptyList(),
        progress: List<ReadingProgressSyncEntry> = emptyList(),
        settings: UserSettingsSync? = null,
    ) = SyncPayload(
        deviceId = deviceId,
        version = version,
        generatedAt = 1,
        library = library,
        readingProgress = progress,
        settings = settings,
    )
}
