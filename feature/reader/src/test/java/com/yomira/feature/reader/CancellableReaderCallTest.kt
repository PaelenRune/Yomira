package com.yomira.feature.reader

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableReaderCallTest {
    @Test fun `leaving reader cancels pending network call immediately`() = runTest {
        val call = PendingCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.readCancellable { error("No response expected") } }
        assertTrue(call.isExecuted())
        job.cancel()
        assertTrue(call.isCanceled())
        job.join()
    }

    @Test fun `a response arriving after dismissal is closed without parsing`() = runTest {
        val call = PendingCall()
        var parsed = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.readCancellable { parsed = true } }
        job.cancel()
        val body = TrackingBody()
        call.callback.onResponse(call, Response.Builder()
            .request(call.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build())
        assertTrue(body.closed)
        assertFalse(parsed)
        job.join()
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
        private val source = object : ForwardingSource(Buffer().writeUtf8("page")) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType() = null
        override fun contentLength(): Long = 4
        override fun source(): BufferedSource = source
    }

    private class PendingCall : Call {
        lateinit var callback: Callback
        private var executed = false
        private var cancelled = false
        override fun request(): Request = Request.Builder().url("https://example.com/chapter").build()
        override fun execute(): Response = error("Must use the asynchronous dispatcher")
        override fun enqueue(responseCallback: Callback) { executed = true; callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = PendingCall()
        // OkHttp 5 Call surface; unused by this fake.
        override fun addEventListener(eventListener: okhttp3.EventListener) { }
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    }
}
