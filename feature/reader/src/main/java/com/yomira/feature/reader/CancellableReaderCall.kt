package com.yomira.feature.reader

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Keep cancellation attached until body parsing ends, not just until headers arrive. */
internal suspend fun <T> Call.readCancellable(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        if (!continuation.isActive) return
                        read(it)
                    }
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                    return
                }
                continuation.resume(result)
            }
        })
    }
