// API compatibility shim (Yomira, module :core:extension-api).
// Host-side injection point for the shim layer; not an upstream FQCN.
package eu.kanade.tachiyomi.shim

import okhttp3.OkHttpClient

/**
 * Small service locator through which the host app supplies the HTTP client
 * (and related settings) that the eu.kanade.tachiyomi.network shims delegate
 * to. The host must call [configure] before any extension code runs; until
 * then, network shims fail fast with [ShimNotSupportedException] instead of
 * silently using an uninstrumented client.
 *
 * This object intentionally holds no other state: extension code runs
 * in-process with the host's permissions, so the host stays in full control
 * of every outgoing request by injecting its instrumented client here.
 */
object ShimServiceLocator {

    const val DEFAULT_USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    @Volatile
    private var injectedClient: OkHttpClient? = null

    @Volatile
    private var injectedUserAgent: String? = null

    /** Injects the host's (instrumented) [client] and optionally its user agent. */
    fun configure(client: OkHttpClient, userAgent: String? = null) {
        injectedClient = client
        if (userAgent != null) injectedUserAgent = userAgent
    }

    /** Clears injected state; intended for tests. */
    fun reset() {
        injectedClient = null
        injectedUserAgent = null
    }

    val httpClient: OkHttpClient
        get() = injectedClient ?: throw ShimNotSupportedException(
            "eu.kanade.tachiyomi.network.NetworkHelper.client - host must inject an OkHttpClient " +
                "via eu.kanade.tachiyomi.shim.ShimServiceLocator.configure(...) before extension code runs",
        )

    val userAgent: String
        get() = injectedUserAgent ?: DEFAULT_USER_AGENT
}
