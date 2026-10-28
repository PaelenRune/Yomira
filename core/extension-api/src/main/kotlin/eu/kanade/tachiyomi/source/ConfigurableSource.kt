// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) ConfigurableSource
// ABI. Required by the audited DEXes (setupPreferenceScreen(PreferenceScreen)V
// is defined by the MangaFire preference UI).
package eu.kanade.tachiyomi.source

import androidx.preference.PreferenceScreen

@Suppress("Unused")
interface ConfigurableSource {

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
