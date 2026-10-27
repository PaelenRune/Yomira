package com.yomira.core.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.Response

/** Implement in the UI layer to open a WebView and suspend until verification finishes. */
fun interface CloudflareChallengeHandler {
    suspend fun solve(url: String, userAgent: String): CloudflareResult
}

/** An empty cookie map means verification was dismissed or did not produce cookies. */
data class CloudflareResult(val cookies: Map<String, String>, val userAgent: String?)

/**
 * Register with addInterceptor (not addNetworkInterceptor). The handler runs on the
 * calling OkHttp thread and must dispatch WebView work to the UI thread itself.
 * Pass the client's CookieJar to retain clearance for subsequent requests; even
 * with NO_COOKIES the captured cookies are sent on the immediate retry.
 */
class CloudflareInterceptor(
    private val handler: CloudflareChallengeHandler,
    private val cookieJar: CookieJar = CookieJar.NO_COOKIES,
    private val userAgent: String = NetworkConfiguration().userAgent,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!isChallenge(response)) return response

        // Use the final URL after redirects, never send clearance to the original host.
        val request = response.request
        if (request.body?.isOneShot() == true || request.body?.isDuplex() == true) return response
        val result = try {
            runBlocking { handler.solve(request.url.toString(), request.header("User-Agent") ?: userAgent) }
        } catch (error: Exception) {
            response.close()
            throw IOException("Cloudflare verification failed for ${request.url.host}", error)
        }
        val cookies = result.cookies.mapNotNull { (name, value) ->
            try {
                Cookie.Builder().name(name).value(value).hostOnlyDomain(request.url.host)
                    .path("/").apply { if (request.url.isHttps) secure() }.build()
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        if (cookies.isEmpty()) return response

        response.close()
        cookieJar.saveFromResponse(request.url, cookies)
        val merged = linkedMapOf<String, String>()
        cookieJar.loadForRequest(request.url).forEach { merged[it.name] = it.value }
        request.headers.values("Cookie").forEach { header ->
            header.split(';').forEach { part ->
                val separator = part.indexOf('=')
                if (separator > 0) merged[part.substring(0, separator).trim()] = part.substring(separator + 1).trim()
            }
        }
        cookies.forEach { merged[it.name] = it.value }
        val retry = request.newBuilder()
            .header("Cookie", merged.entries.joinToString("; ") { (name, value) -> "$name=$value" })
            .apply { result.userAgent?.let { header("User-Agent", it) } }
            .build()
        // Deliberately retry only once, even if the server challenges again.
        return chain.proceed(retry)
    }

    private fun isChallenge(response: Response): Boolean {
        if (response.code != 403 && response.code != 503) return false
        if (response.headers.values("Server").any { it.contains("cloudflare", ignoreCase = true) }) return true
        val preview = response.peekBody(64 * 1024).string()
        return listOf("cf-turnstile", "cf-challenge", "challenge-platform", "just a moment...")
            .any { preview.contains(it, ignoreCase = true) }
    }
}
