package com.yomira.app.update

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun downloader(bytes: ByteArray, code: Int = 200) = ApkDownloader(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(bytes.toResponseBody()).build()
        }.build(),
    )

    @Test fun `complete download publishes apk and reports progress`() = runTest {
        val bytes = ByteArray(32_000) { it.toByte() }
        val progress = mutableListOf<Float?>()
        val directory = temporary.newFolder()
        val file = downloader(bytes).download("https://example.com/update.apk", directory, progress::add)
        assertEquals("yomira-update.apk", file.name)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(null, progress.first())
        assertEquals(1f, progress.last())
        assertFalse(directory.resolve("yomira-update.apk.part").exists())
    }

    @Test fun `empty response never publishes apk`() = runTest {
        val directory = temporary.newFolder()
        try {
            downloader(byteArrayOf()).download("https://example.com/update.apk", directory) {}
            fail("Expected empty download to fail")
        } catch (_: IOException) { }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `server error never publishes apk`() = runTest {
        val directory = temporary.newFolder()
        try {
            downloader("error".toByteArray(), 503).download("https://example.com/update.apk", directory) {}
            fail("Expected HTTP error")
        } catch (_: IOException) { }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `insecure download is rejected before network access`() = runTest {
        try {
            downloader(byteArrayOf(1)).download("http://example.com/update.apk", temporary.newFolder()) {}
            fail("Expected HTTPS requirement")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun `cancellation deletes partial file and preserves previous complete apk`() = runTest {
        val directory = temporary.newFolder()
        val previous = directory.resolve("yomira-update.apk").apply { writeText("previous") }
        try {
            downloader(ByteArray(32_000)).download("https://example.com/update.apk", directory) {
                if (it != null) throw CancellationException("cancelled")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals("previous", previous.readText())
        assertFalse(directory.resolve("yomira-update.apk.part").exists())
    }
}
