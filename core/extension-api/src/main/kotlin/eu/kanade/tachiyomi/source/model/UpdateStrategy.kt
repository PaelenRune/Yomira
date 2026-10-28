// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) UpdateStrategy
// ABI. Not referenced by the audited DEXes, but SManga.update_strategy needs
// it to compile and to resolve at class-load time.
package eu.kanade.tachiyomi.source.model

@Suppress("Unused")
enum class UpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}
