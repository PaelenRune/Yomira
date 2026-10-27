package com.yomira.core.network

import java.io.IOException
import kotlinx.coroutines.delay
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.BufferedSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareInterceptorTest {
    @Test
    fun `detects challenge markers without consuming response body`() {
        for (code in listOf(403, 503)) {
            for (marker in listOf("cf-turnstile", "cf-challenge", "challenge-platform", "Just a moment...")) {
                MockWebServer().use { server ->
                    server.enqueue(MockResponse().setResponseCode(code).setBody(marker))
                    var calls = 0
                    val client = client(CloudflareChallengeHandler { _, _ ->
                        calls++
                        CloudflareResult(emptyMap(), null)
                    })
                    client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                        assertEquals(code, it.code)
                        assertEquals(marker, it.body!!.string())
                    }
                    assertEquals(1, calls)
                    assertEquals(1, server.requestCount)
                }
            }
        }
    }

    @Test
    fun `captures cookies and user agent and automatically retries`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "Cloudflare"))
            server.enqueue(MockResponse().setBody("chapter"))
            val client = client(CloudflareChallengeHandler { url, userAgent ->
                delay(1)
                assertEquals(server.url("/chapter").toString(), url)
                assertEquals("original-agent", userAgent)
                CloudflareResult(mapOf("cf_clearance" to "clearance", "session" to "verified"), "webview-agent")
            })
            val request = Request.Builder().url(server.url("/chapter"))
                .header("User-Agent", "original-agent")
                .header("Cookie", "existing=keep; cf_clearance=expired").build()
            client.newCall(request).execute().use { assertEquals("chapter", it.body!!.string()) }
            server.takeRequest()
            val retry = server.takeRequest()
            assertEquals("webview-agent", retry.getHeader("User-Agent"))
            assertEquals("existing=keep; cf_clearance=clearance; session=verified", retry.getHeader("Cookie"))
            assertEquals("/chapter", retry.path)
        }
    }

    @Test
    fun `saves cookies in configured jar for subsequent requests`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("cf-turnstile"))
            server.enqueue(MockResponse().setBody("retried"))
            server.enqueue(MockResponse().setBody("next"))
            val jar = object : CookieJar {
                var cookies = emptyList<Cookie>()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this.cookies = cookies }
                override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
            }
            val client = client(CloudflareChallengeHandler { _, _ ->
                CloudflareResult(mapOf("cf_clearance" to "saved"), null)
            }, jar)
            repeat(2) {
                client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            }
            server.takeRequest()
            assertEquals("cf_clearance=saved", server.takeRequest().getHeader("Cookie"))
            assertEquals("cf_clearance=saved", server.takeRequest().getHeader("Cookie"))
            val cookie = jar.cookies.single()
            assertTrue(cookie.hostOnly)
            assertFalse(cookie.matches(HttpUrl.Builder().scheme("http").host("other.example").build()))
        }
    }

    @Test
    fun `normal success and unrelated forbidden responses pass through`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("cf-turnstile").setHeader("Server", "cloudflare"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("Permission denied"))
            val client = client(CloudflareChallengeHandler { _, _ -> error("Must not solve") })
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                assertEquals(200, it.code)
                assertEquals("cf-turnstile", it.body!!.string())
            }
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                assertEquals(403, it.code)
                assertEquals("Permission denied", it.body!!.string())
            }
        }
    }

    @Test
    fun `persistent challenge is not retried indefinitely`() {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "cloudflare")) }
            var solves = 0
            val client = client(CloudflareChallengeHandler { _, _ ->
                solves++
                CloudflareResult(mapOf("cf_clearance" to "rejected"), null)
            })
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                assertEquals(403, it.code)
            }
            assertEquals(1, solves)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `handler failure becomes an IO failure`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "cloudflare"))
            val client = client(CloudflareChallengeHandler { _, _ -> error("WebView unavailable") })
            try {
                client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { }
                throw AssertionError("Expected IOException")
            } catch (error: IOException) {
                assertEquals("WebView unavailable", error.cause?.message)
            }
        }
    }

    @Test
    fun `one shot request bodies are never replayed`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "cloudflare"))
            val client = client(CloudflareChallengeHandler { _, _ -> error("Must not solve") })
            val body = object : RequestBody() {
                override fun contentType() = null
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) { sink.writeUtf8("non-replayable") }
            }
            val request = Request.Builder().url(server.url("/")).post(body).build()
            client.newCall(request).execute().use { assertEquals(403, it.code) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `redirected challenge is solved and retried at its final URL`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/challenge"))
            server.enqueue(MockResponse().setResponseCode(403).setHeader("Server", "cloudflare"))
            server.enqueue(MockResponse().setBody("verified"))
            val client = client(CloudflareChallengeHandler { url, _ ->
                assertEquals(server.url("/challenge").toString(), url)
                CloudflareResult(mapOf("cf_clearance" to "solved"), null)
            })
            client.newCall(Request.Builder().url(server.url("/original")).build()).execute().use {
                assertEquals("verified", it.body!!.string())
            }
            server.takeRequest()
            server.takeRequest()
            assertEquals("/challenge", server.takeRequest().path)
        }
    }

    private fun client(handler: CloudflareChallengeHandler, jar: CookieJar = CookieJar.NO_COOKIES): OkHttpClient =
        OkHttpClient.Builder().cookieJar(jar).addInterceptor(CloudflareInterceptor(handler, jar)).build()
}
