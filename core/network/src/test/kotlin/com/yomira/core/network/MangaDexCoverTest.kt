package com.yomira.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaDexCoverTest {
    @Test fun englishCoverBeatsUnknownAndEveryNonEnglishLocale() = runTest {
        assertCover(
            """{"type":"cover_art","attributes":{"fileName":"ja.jpg","locale":"ja"}},
                {"type":"cover_art","attributes":{"fileName":"unknown.jpg"}},
                {"type":"cover_art","attributes":{"fileName":"ko.jpg","locale":"ko"}},
                {"type":"cover_art","attributes":{"fileName":"zh.jpg","locale":"zh"}},
                {"type":"cover_art","attributes":{"fileName":"en.jpg","locale":"en"}}""",
            "en.jpg", "en",
        )
    }

    @Test fun unknownLocaleBeatsKnownNonEnglishFallback() = runTest {
        assertCover(
            """{"type":"cover_art","attributes":{"fileName":"ja.jpg","locale":"ja"}},
                {"type":"cover_art","attributes":{"fileName":"unknown.jpg","locale":" "}}""",
            "unknown.jpg", null,
        )
    }

    @Test fun englishLocaleWhitespaceIsNormalized() = runTest {
        assertCover(
            """{"type":"cover_art","attributes":{"fileName":"ja.jpg","locale":"ja"}},
                {"type":"cover_art","attributes":{"fileName":"en.jpg","locale":" en "}}""",
            "en.jpg", "en",
        )
    }

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

    @Test fun originalLanguageBeatsUnknownAndTranslatedCovers() = runTest {
        assertCover("""{"type":"cover_art","attributes":{"fileName":"fr.jpg","locale":"fr"}},
            {"type":"cover_art","attributes":{"fileName":"unknown.jpg"}},
            {"type":"cover_art","attributes":{"fileName":"ko.jpg","locale":"ko-KR"}}""",
            "ko.jpg", "ko-KR", "ko")
    }

    @Test fun regionalEnglishCoverWins() = runTest {
        assertCover("""{"type":"cover_art","attributes":{"fileName":"ja.jpg","locale":"ja"}},
            {"type":"cover_art","attributes":{"fileName":"en.jpg","locale":"en-US"}}""",
            "en.jpg", "en-US", "ja")
    }

    private suspend fun assertCover(relationships: String, fileName: String, locale: String?, original: String = "") {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""
                {"data":{"id":"series","attributes":{"title":{"en":"Series"},"originalLanguage":"$original"},
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
