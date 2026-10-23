package com.yomira.data.extension

import com.yomira.domain.extension.requireHttps
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpRepositoryClient(client: OkHttpClient) : RepositoryClient {
    private val client = client.newBuilder()
        .followSslRedirects(false)
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(indexUrl: String): String {
        requireHttps(indexUrl)
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(indexUrl).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            requireHttps(it.request.url.toString())
                            if (!it.isSuccessful) throw IOException("Repository HTTP ${it.code}")
                            val body = it.body ?: throw IOException("Empty repository response")
                            if (body.contentLength() > MAX_BYTES) throw IOException("Repository index too large")
                            val source = body.source()
                            if (source.request(MAX_BYTES + 1)) throw IOException("Repository index too large")
                            source.readUtf8()
                        }
                    }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
    }

    private companion object { const val MAX_BYTES = 16L * 1024 * 1024 }
}
