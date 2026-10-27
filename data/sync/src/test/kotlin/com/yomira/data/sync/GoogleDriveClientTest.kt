package com.yomira.data.sync

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GoogleDriveClientTest {
    private val codec = SyncPayloadCodec()
    private val payload = SyncPayload(deviceId = "phone")

    @Test fun `duplicate initial documents are downloaded rather than silently discarded`() = runTest {
        val requests = mutableListOf<Request>()
        val tablet = payload.copy(deviceId = "tablet")
        val client = client(requests, listOf(
            Reply("""{"files":[{"id":"phone-doc"},{"id":"tablet-doc"}]}"""),
            Reply(codec.encode(payload), etag = "phone-rev"),
            Reply(codec.encode(tablet), etag = "tablet-rev")))
        val result = client.fetch()!!
        assertEquals(payload, result.payload)
        assertEquals(listOf(tablet), result.additionalPayloads)
        assertEquals(3, requests.size)
    }

    @Test fun `fetch takes revision from HTTP header not unsupported v3 etag field`() = runTest {
        val requests = mutableListOf<Request>()
        val client = client(requests, listOf(
            Reply("""{"files":[{"id":"doc"}]}"""),
            Reply(codec.encode(payload), etag = "\"rev1\""),
        ))
        val result = client.fetch()!!
        assertEquals("\"rev1\"", result.revision.etag)
        assertFalse(requests[0].url.queryParameter("fields")!!.contains("etag"))
        assertEquals("appDataFolder", requests[0].url.queryParameter("spaces"))
        assertEquals("media", requests[1].url.queryParameter("alt"))
    }

    @Test fun `newly discovered remote document causes retry instead of blind overwrite`() = runTest {
        val requests = mutableListOf<Request>()
        val client = client(requests, listOf(Reply("""{"files":[{"id":"other-device"}]}""")))
        try { client.push(payload, null); fail("Expected write conflict") }
        catch (_: SyncWriteConflictException) { }
        assertEquals(1, requests.size)
        assertEquals("GET", requests.single().method)
    }

    @Test fun `update sends if match and reports concurrent write`() = runTest {
        val requests = mutableListOf<Request>()
        val client = client(requests, listOf(Reply("{}", code = 412)))
        try { client.push(payload, RemoteSyncRevision("doc", "\"rev1\"")); fail("Expected conflict") }
        catch (_: SyncWriteConflictException) { }
        assertEquals("\"rev1\"", requests.single().header("If-Match"))
    }

    @Test fun `missing token does not perform a network call`() = runTest {
        val requests = mutableListOf<Request>()
        val client = client(requests, emptyList(), token = null)
        try { client.fetch(); fail("Expected sign in required") }
        catch (_: SyncNotAuthenticatedException) { }
        assertTrue(requests.isEmpty())
    }

    private data class Reply(val body: String, val code: Int = 200, val etag: String? = null)

    private fun client(requests: MutableList<Request>, replies: List<Reply>, token: String? = "token"): GoogleDriveClient {
        var index = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val reply = replies[index++]
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(reply.code).message("Test response").body(reply.body.toResponseBody())
                .apply { reply.etag?.let { header("ETag", it) } }.build()
        }.build()
        return GoogleDriveClient(http, DriveAccessTokenProvider { token }, codec)
    }
}
