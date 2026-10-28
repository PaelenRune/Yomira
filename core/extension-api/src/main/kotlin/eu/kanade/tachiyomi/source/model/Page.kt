// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) Page ABI.
// The constructor signature (including android.net.Uri and the Kotlin
// $default synthetic) is pinned by the audited DEXes.
package eu.kanade.tachiyomi.source.model

import android.net.Uri

@Suppress("Unused")
class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
    var uri: Uri? = null,
)
