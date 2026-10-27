package com.yomira.data.extension

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test

class ExtensionDownloadClientTest {
    // Preserve HTTPS validation at the public boundary, rewriting only inside the test transport.
    private fun downloader(server: MockWebServer, cap: Long = 16) = OkHttpExtensionDownloadClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request().newBuilder().url(server.url("/extension.apk")).build())
            response.newBuilder().request(chain.request()).build()
        }.build(), cap,
    )

    @Test fun `normal and unknown length APKs return all bytes`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("dex payload"))
            server.enqueue(MockResponse().setChunkedBody("dex payload", 3))
            val client = downloader(server)
            repeat(2) { assertArrayEquals("dex payload".toByteArray(), client.download("https://example.org/extension.apk")) }
        }
    }

    @Test fun `size limit rejects chunked payload over cap`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("123456789", 2))
            try {
                downloader(server, 8).download("https://example.org/extension.apk")
                fail("Expected byte cap rejection")
            } catch (_: IOException) { }
        }
    }
}
