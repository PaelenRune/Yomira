// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) UnmeteredSource
// ABI. Not referenced by the audited DEXes; shipped for ABI completeness.
package eu.kanade.tachiyomi.source

/**
 * A source that explicitly doesn't require traffic considerations.
 *
 * This typically applies for self-hosted sources.
 */
@Suppress("Unused")
interface UnmeteredSource
