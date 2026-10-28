// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 NetworkHelper ABI (getClient()) but
// delegates to the host-injected client via ShimServiceLocator instead of
// building its own. Upstream builds a full client stack here; that is the
// host's job in this architecture.
package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.shim.ShimServiceLocator
import okhttp3.OkHttpClient

@Suppress("Unused", "UNUSED_PARAMETER")
class NetworkHelper(context: Context? = null) {

    /**
     * The HTTP client extension code uses. Delegates to the host-injected
     * client; throws [eu.kanade.tachiyomi.shim.ShimNotSupportedException]
     * (naming this member) until the host has configured one.
     */
    val client: OkHttpClient
        get() = ShimServiceLocator.httpClient

    /** Default User-Agent for [eu.kanade.tachiyomi.source.online.HttpSource] requests. */
    val defaultUserAgent: String
        get() = ShimServiceLocator.userAgent
}
