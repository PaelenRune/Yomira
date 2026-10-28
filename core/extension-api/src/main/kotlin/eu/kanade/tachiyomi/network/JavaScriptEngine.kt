// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 JavaScriptEngine ABI. The audited
// MangaFire/WeebCentral/Toonily DEXes do NOT reference this class; it exists
// for ABI completeness only and is listed as KNOWN-MISSING: evaluation always
// throws [ShimNotSupportedException] because this module bundles no JS engine.
package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.shim.ShimNotSupportedException

@Suppress("Unused", "UNUSED_PARAMETER")
class JavaScriptEngine(context: Context) {

    /**
     * KNOWN-MISSING: always throws. The host would need to provide a QuickJS
     * (or equivalent) engine behind this signature.
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun <T> evaluate(script: String): T =
        throw ShimNotSupportedException("eu.kanade.tachiyomi.network.JavaScriptEngine.evaluate(script)")
}
