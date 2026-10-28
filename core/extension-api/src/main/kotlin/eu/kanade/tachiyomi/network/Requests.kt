// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 Requests ABI (GET/GET$default/POST)
// with the real Mihon request-building semantics.
@file:Suppress("Unused", "FunctionName")

package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

private val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().noCache().build()

private val DEFAULT_HEADERS: Headers = Headers.Builder()
    .set("User-Agent", eu.kanade.tachiyomi.shim.ShimServiceLocator.userAgent)
    .build()

private val DEFAULT_BODY: RequestBody = ByteArray(0).toRequestBody("text/plain".toMediaType())

fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .post(body)
    .cacheControl(cache)
    .build()
