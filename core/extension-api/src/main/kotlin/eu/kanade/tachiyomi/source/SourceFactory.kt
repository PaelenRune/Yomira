// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) SourceFactory ABI.
// Required by the audited DEXes (their entry points implement this interface).
package eu.kanade.tachiyomi.source

@Suppress("Unused")
interface SourceFactory {

    fun createSources(): List<Source>
}
