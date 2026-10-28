// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) ABI so that
// third-party extension DEXes can link in-process. Not an upstream file.
package eu.kanade.tachiyomi.shim

/**
 * Thrown by shim members that exist for link compatibility but have no
 * functional implementation in this module. The message always names the
 * exact member so failures are diagnosable from logs.
 */
class ShimNotSupportedException(member: String) :
    UnsupportedOperationException("Extension API shim member not implemented: $member")
