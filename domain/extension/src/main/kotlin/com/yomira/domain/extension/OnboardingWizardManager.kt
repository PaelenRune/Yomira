package com.yomira.domain.extension

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OnboardingWizardManager(
    private val extensions: ExtensionRepository,
    private val store: OnboardingStore,
) {
    private val mutex = Mutex()

    suspend fun state(): OnboardingState? = store.read()

    /** Explicit opt-in only. Downloads metadata, never silently installs or trusts an APK. */
    suspend fun complete(
        preferences: ReaderPreferences,
        integrateKeiyoushi: Boolean,
    ): OnboardingState = mutex.withLock {
        val repoUrl = KEIYOUSHI_INDEX.takeIf { integrateKeiyoushi }
        val suggestions = if (repoUrl != null) {
            extensions.addRepository(repoUrl)
            extensions.refresh().filter { extension ->
                (preferences.allowAdultContent || !extension.nsfw) &&
                    extension.sources.any { it.language == preferences.language.code || it.language == "all" }
            }.sortedBy { it.packageName }.map { it.packageName }
        } else emptyList()
        OnboardingState(preferences, repoUrl, suggestions, completed = true).also { store.write(it) }
    }

    companion object {
        const val KEIYOUSHI_INDEX = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
    }
}
