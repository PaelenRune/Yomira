package com.yomira.desktop.data

import com.yomira.core.network.NetworkConfiguration
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Composition root of the desktop target. One shared OkHttp pool feeds the
 * metadata waterfall, the reader and artwork, matching the Android app's single
 * client so connection reuse and timeouts behave the same way.
 */
class DesktopEnvironment private constructor(
    val catalog: CatalogRepository,
    val chapters: ChapterRepository,
    /** Shared pool used by the catalogue, the reader and remote artwork. */
    val images: OkHttpClient,
) : AutoCloseable {

    override fun close() {
        images.dispatcher.executorService.shutdown()
        images.connectionPool.evictAll()
    }

    companion object {
        /** Desktop user agent keeps provider rate limits attributable to this target. */
        const val USER_AGENT = "Yomira-Desktop/0.4.0"

        fun create(): DesktopEnvironment {
            val config = NetworkConfiguration(
                userAgent = USER_AGENT,
                // Optional key: without it ComicVine simply opts out of the waterfall.
                comicVineApiKey = System.getenv("YOMIRA_COMICVINE_API_KEY").orEmpty(),
            )
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            return DesktopEnvironment(
                catalog = DefaultCatalogRepository(client, config),
                chapters = MangaDexChapterRepository(client, config),
                images = client,
            )
        }
    }
}

/**
 * Opens an already-validated HTTPS page in the user's default browser. Returns
 * `false` when the platform cannot browse, so the UI can show an honest message.
 */
fun openInBrowser(url: String?): Boolean {
    val target = url?.takeIf { it.startsWith("https://") } ?: return false
    return try {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        desktop.browse(URI(target))
        true
    } catch (_: Exception) {
        false
    }
}
