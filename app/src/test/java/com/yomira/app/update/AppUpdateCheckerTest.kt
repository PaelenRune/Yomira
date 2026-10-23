package com.yomira.app.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    private val apk = "https://github.com/PaelenRune/Yomira/releases/download/v0.2.0-beta/yomira-v0.2.0-beta.apk"
    private val page = "https://github.com/PaelenRune/Yomira/releases/tag/v0.2.0-beta"

    @Test fun newerReleaseReturnsVersionNotesAndApk() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.2.0-beta")))
            val info = checker(server).check()

            assertEquals("v0.2.0-beta", info?.version)
            assertEquals("- Daha hızlı açılış\n- Katalog düzeltmeleri", info?.releaseNotes)
            assertEquals(apk, info?.apkDownloadUrl)
            assertEquals(page, info?.releasePageUrl)
        }
    }

    @Test fun sameVersionDoesNotNotify() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.1.0-beta")))
            assertNull(checker(server).check())
        }
    }

    @Test fun olderVersionDoesNotNotify() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.1.0-alpha")))
            assertNull(checker(server).check())
        }
    }

    @Test fun draftReleaseIsIgnored() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.9.0", draft = true)))
            assertNull(checker(server).check())
        }
    }

    @Test fun missingApkFallsBackToReleasePage() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.2.0-beta", assets = "[]")))
            val info = checker(server).check()
            assertEquals(page, info?.apkDownloadUrl)
        }
    }

    @Test fun insecureDownloadUrlIsNeverHandedToTheBrowser() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    release(
                        tag = "v0.2.0-beta",
                        assets = """[{"name":"yomira.apk","browser_download_url":"http://insecure.example.com/yomira.apk"}]""",
                    ),
                ),
            )
            val info = checker(server).check()
            assertEquals(page, info?.apkDownloadUrl)
            assertFalse(info!!.apkDownloadUrl.startsWith("http://"))
        }
    }

    @Test fun releaseWithoutAnyUsableUrlIsIgnored() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.2.0-beta", htmlUrl = "not-a-url", assets = "[]")))
            assertNull(checker(server).check())
        }
    }

    @Test fun blankNotesUseAFriendlyFallback() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.2.0-beta", body = "   ")))
            val info = checker(server).check()
            assertTrue(info!!.releaseNotes.isNotBlank())
        }
    }

    @Test fun serverErrorIsSilent() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            assertNull(checker(server).check())
        }
    }

    @Test fun malformedPayloadIsSilent() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<html>rate limited</html>"))
            assertNull(checker(server).check())
        }
    }

    @Test fun semanticComparisonHandlesTagsPrereleasesAndSegments() {
        assertTrue(AppVersion.isNewer("v0.2.0", "0.1.0-beta"))
        assertFalse(AppVersion.isNewer("0.1.0-beta", "0.1.0-beta"))
        assertTrue(AppVersion.isNewer("0.1.0", "0.1.0-beta"))
        assertFalse(AppVersion.isNewer("0.1.0-alpha", "0.1.0-beta"))
        assertTrue(AppVersion.isNewer("0.1.0-beta.2", "0.1.0-beta"))
        assertTrue(AppVersion.isNewer("0.10.0", "0.9.9"))
        assertFalse(AppVersion.isNewer("garbage", "0.1.0"))
        assertFalse(AppVersion.isNewer("0.2.0", ""))
    }

    private fun checker(server: MockWebServer, current: String = "0.1.0-beta") =
        AppUpdateChecker(
            httpClient = OkHttpClient(),
            currentVersion = current,
            endpoint = server.url("/"),
        )

    private fun release(
        tag: String = "v0.2.0-beta",
        draft: Boolean = false,
        body: String = "- Daha hızlı açılış\\n- Katalog düzeltmeleri",
        htmlUrl: String = page,
        assets: String = """[{"name":"yomira-v0.2.0-beta.apk","browser_download_url":"$apk"}]""",
    ): String = """
        {
          "tag_name": "$tag",
          "name": "Yomira $tag",
          "body": "$body",
          "html_url": "$htmlUrl",
          "draft": $draft,
          "prerelease": true,
          "assets": $assets
        }
    """.trimIndent()

    @Test fun notesAreTrimmedButKeepTheirLineBreaks() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release(tag = "v0.2.0-beta", body = "  \\n- One\\n- Two\\n  ")))
            val info = checker(server).check()!!
            assertEquals("- One\n- Two", info.releaseNotes)
        }
    }
}
