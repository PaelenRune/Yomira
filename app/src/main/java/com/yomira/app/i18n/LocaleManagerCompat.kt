package com.yomira.app.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app bilingual switcher (English default, Turkish overlay).
 *
 * On API 33+ the framework delegates to the system `LocaleManager`, which persists the
 * choice and shows it under system > apps > languages. Below 33, AppCompat stores the
 * locale (enabled by `autoStoreLocales` in the manifest) and recreates every activity,
 * so `values/strings.xml` (English) and `values-tr/strings.xml` resolve in the new
 * language instantly — the user never has to leave or restart the app.
 */
object LocaleManagerCompat {
    const val ENGLISH = "en"
    const val TURKISH = "tr"

    /** Set once the onboarding language has been synced for this process. */
    private var startupLocaleSynced = false

    /** Only these two UI languages ship; anything else falls back to English. */
    fun supportedTag(languageTag: String?): String =
        if (languageTag?.startsWith(TURKISH, ignoreCase = true) == true) TURKISH else ENGLISH

    /** True when the switch was applied; false means the locale was already active. */
    fun setApplicationLocales(languageTag: String): Boolean {
        val tag = supportedTag(languageTag)
        if (currentApplicationTag() == tag) return false
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        return true
    }

    /**
     * Startup sync of the language saved by the onboarding wizard.
     *
     * Applying a locale recreates every activity. On Samsung One UI 6 (Android 14) the
     * framework can keep reporting the previous app locale for a moment while the activity
     * is being recreated, so an unconditional re-apply on every `onCreate` turns into an
     * endless recreate loop. This method applies the stored language at most once per
     * process, which breaks that loop without dropping the choice on a later launch.
     */
    fun syncApplicationLocalesOnStartup(languageTag: String): Boolean {
        if (startupLocaleSynced) return false
        startupLocaleSynced = true
        return setApplicationLocales(languageTag)
    }

    /**
     * Active UI language: the explicit in-app choice when present, otherwise the device
     * language restricted to the two supported tags — mirroring how resource resolution
     * picked `values-tr` for Turkish devices on first launch.
     */
    fun currentApplicationTag(): String {
        val requested = AppCompatDelegate.getApplicationLocales()
        if (!requested.isEmpty) {
            requested.toLanguageTags().takeIf { it.isNotBlank() }?.let { locale ->
                return supportedTag(locale.substringBefore(','))
            }
        }
        return supportedTag(androidx.core.os.LocaleListCompat.getAdjustedDefault().firstTag())
    }

    fun isTurkish(): Boolean = currentApplicationTag() == TURKISH

    /** Toggle used by the header button; returns the newly active language tag. */
    fun toggleApplicationLocale(): String =
        if (isTurkish()) {
            setApplicationLocales(ENGLISH); ENGLISH
        } else {
            setApplicationLocales(TURKISH); TURKISH
        }

    private fun androidx.core.os.LocaleListCompat.firstTag(): String? =
        if (isEmpty) null else toLanguageTags().substringBefore(',').takeIf { it.isNotBlank() }
}

/**
 * Kept for callers that hold a [Context]: resource lookups outside the composition
 * (e.g. `getString` in view models) always follow the currently active app locale.
 */
fun Context.currentLanguageTag(): String = LocaleManagerCompat.currentApplicationTag()
