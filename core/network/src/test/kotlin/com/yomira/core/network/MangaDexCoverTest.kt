package com.yomira.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaDexCoverTest {
    @Test fun englishCoverWinsRegardlessOfRelationshipOrder() = runTest {
        assertCover(
            """{"type":"cover_art","attributes":{"fileName":"ja.jpg","locale":"ja"}},
                {"type":"cover_art","attributes":{"fileName":"en.jpg","locale":"EN"}}""",
            "en.jpg", "EN",
        )
    }

    @Test fun japaneseCoverIsRetainedWithoutEnglishCover() = runTest {
        assertCover(
            """{"type":"cover_art","attributes":{"fileName":"ja.jpg","locale":"ja"}}""",
            "ja.jpg", "ja",
        )
    }

    @Test fun missingEnglishFileDoesNotHideUsableFallback() = runTest {
        assertCover(
            """{"type":"cover_art","attributes":{"fileName":" ","locale":"en"}},
                {"type":"cover_art","attributes":{"fileName":"ja.jpg","locale":"ja"}}""",
            "ja.jpg", "ja",
        )
    }

    @Test fun legacyCoverWithoutLocaleStillWorks() = runTest {
        assertCover(
            """{"type":"cover_art","attributes":{"fileName":"legacy.jpg"}}""",
            "legacy.jpg", null,
        )
    }

    private suspend fun assertCover(relationships: String, fileName: String, locale: String?) {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"id":"series","attributes":{"title":{"en":"Series"}},
                "relationships":[$relationships]}}
            """.trimIndent()))
            server.enqueue(MockResponse().setBody("""{"statistics":{}}"""))
            val client = MangaDexClient(
                OkHttpClient(),
                config = NetworkConfiguration(
                    mangaDexBaseUrl = server.url("/").toString().trimEnd('/'),
                    mangaDexUploadsUrl = "https://uploads.test",
                ),
            )
            val metadata = client.findById("series")!!
            assertEquals("https://uploads.test/covers/series/$fileName.512.jpg", metadata.coverUrl)
            assertEquals(locale, metadata.coverLocale)
        }
    }
}
