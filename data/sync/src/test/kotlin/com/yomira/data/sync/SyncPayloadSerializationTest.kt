package com.yomira.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncPayloadSerializationTest {
    private val codec = SyncPayloadCodec()

    @Test
    fun `version two includes favorites history and portable extension configurations`() {
        val payload = SyncPayload(deviceId = "phone",
            library = listOf(LibrarySyncEntry("m", favorite = true,
                categories = listOf("Reading", "Favorites"), customTags = listOf("weekend"))),
            readingProgress = listOf(ReadingProgressSyncEntry("m", 2.0, 9, 123,
                chapterId = "chapter-2", completed = true)),
            readingHistory = listOf(ReadingHistorySyncEntry("m", "chapter-2", 2, 9, 10, 123, true)),
            extensions = listOf(InstalledExtensionSyncEntry("example.extension", 12,
                listOf("https://example.org/index.json"), mapOf("language" to "tr"), 124)))
        assertEquals(payload, codec.decode(codec.encode(payload)))
    }

    @Test
    fun `legacy schema migrates without losing progress`() {
        val legacy = codec.decode("""{"schemaVersion":1,"deviceId":"old","readingProgress":[{"mangaId":"m","chapterNumber":3,"pageIndex":2,"lastReadAt":100}]}""")
        assertEquals(100L, legacy.readingProgress.single().lastReadAt)
        assertTrue(legacy.readingHistory.isEmpty())
        assertTrue(legacy.extensions.isEmpty())
    }

    @Test
    fun `duplicate chapter identities and insecure extension repositories are rejected`() {
        assertInvalid("""{"deviceId":"d","readingHistory":[{"mangaId":"m","chapterId":"c"},{"mangaId":"m","chapterId":"c"}]}""")
        assertInvalid("""{"deviceId":"d","extensions":[{"packageId":"p","versionCode":1,"repositorySources":["http://example.org"]}]}""")
    }

    @Test
    fun `full payload round trips without losing data`() {
        val payload = SyncPayload(
            deviceId = "device-ä-1",
            version = 42,
            generatedAt = 1_700_000_000_000,
            library = listOf(
                LibrarySyncEntry("source:1", true, "Favoriler", 10),
                LibrarySyncEntry("source:2", false, null, 20),
            ),
            readingProgress = listOf(
                ReadingProgressSyncEntry("source:1", 12.5, 9, 30),
            ),
            settings = UserSettingsSync(
                gridColumns = 4,
                adultEnabled = true,
                theme = "oled",
                updatedAt = 40,
            ),
        )

        val decoded = codec.decode(codec.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun `defaults are applied to a minimal document`() {
        val decoded = codec.decode(
            """{"deviceId":"device-a","library":[{"mangaId":"m"}],"readingProgress":[]}""",
        )

        assertEquals(SyncPayload.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1, decoded.library.size)
        assertTrue(decoded.library.single().inLibrary)
        assertNull(decoded.library.single().category)
        assertNull(decoded.settings)
    }

    @Test
    fun `unknown keys from a newer client are ignored`() {
        val decoded = codec.decode(
            """{
                "deviceId":"device-a",
                "futureField":{"nested":true},
                "settings":{"gridColumns":2,"adultEnabled":false,"theme":"oled","newOption":"x"}
            }""",
        )

        assertEquals(2, decoded.settings?.gridColumns)
    }

    @Test
    fun `encoded collections are canonically ordered`() {
        val payload = SyncPayload(
            deviceId = "device-a",
            library = listOf(
                LibrarySyncEntry("z", true),
                LibrarySyncEntry("a", true),
            ),
            readingProgress = listOf(
                ReadingProgressSyncEntry("z", 1.0, 0, 1),
                ReadingProgressSyncEntry("a", 1.0, 0, 1),
            ),
        )

        val encoded = codec.encode(payload)
        val decoded = codec.decode(encoded)

        assertEquals(listOf("a", "z"), decoded.library.map { it.mangaId })
        assertEquals(listOf("a", "z"), decoded.readingProgress.map { it.mangaId })
    }

    @Test
    fun `duplicate manga ids are rejected`() {
        assertInvalid(
            """{"deviceId":"d","library":[{"mangaId":"m"},{"mangaId":"m"}]}""",
        )
    }

    @Test
    fun `negative and non-finite progress is rejected`() {
        assertInvalid(
            """{"deviceId":"d","readingProgress":[{"mangaId":"m","chapterNumber":-1,"pageIndex":0}]}""",
        )
        assertInvalid(
            """{"deviceId":"d","readingProgress":[{"mangaId":"m","chapterNumber":1,"pageIndex":-1}]}""",
        )
    }

    @Test
    fun `unsupported schema versions are rejected instead of guessed`() {
        assertInvalid(
            """{"deviceId":"d","schemaVersion":${SyncPayload.CURRENT_SCHEMA_VERSION + 1}}""",
        )
    }

    @Test
    fun `invalid grid columns and blank device ids are rejected`() {
        assertInvalid(
            """{"deviceId":"d","settings":{"gridColumns":9,"adultEnabled":false,"theme":"oled"}}""",
        )
        assertInvalid("""{"deviceId":"","generatedAt":0}""")
    }

    @Test
    fun `malformed json and empty input raise the public exception`() {
        assertInvalid("")
        assertInvalid("{not json}")
        assertInvalid("[]")
    }

    @Test
    fun `oversized documents are rejected before upload`() {
        val hugeCategory = "x".repeat(SyncPayloadCodec.MAX_PAYLOAD_BYTES)
        val payload = SyncPayload(
            deviceId = "d",
            library = listOf(LibrarySyncEntry("m", true, hugeCategory)),
        )

        try {
            codec.encode(payload)
            fail("Expected SyncPayloadException")
        } catch (_: SyncPayloadException) {
            // Expected: a runaway document must never reach Drive.
        }
    }

    private fun assertInvalid(json: String) {
        try {
            codec.decode(json)
            fail("Expected SyncPayloadException for: $json")
        } catch (_: SyncPayloadException) {
            // Expected.
        }
    }

    @Test
    fun `codec rejects a payload whose utf8 bytes exceed the cap`() {
        val oversized = SyncPayload(
            deviceId = "d",
            library = listOf(
                LibrarySyncEntry("m", true, "é".repeat(SyncPayloadCodec.MAX_PAYLOAD_BYTES / 2 + 1)),
            ),
        )

        try {
            codec.encode(oversized)
            fail("Expected SyncPayloadException")
        } catch (_: SyncPayloadException) {
            // Expected.
        }
        assertFalse(oversized.library.isEmpty())
    }
}
