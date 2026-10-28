package com.yomira.domain.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OnboardingWizardManager(
    private val extensions: ExtensionRepository,
    private val store: OnboardingStore,
) {
    private val mutex = Mutex()

    suspend fun state(): OnboardingState? = store.read()

    /**
     * Completes onboarding. The app ships without any repository: only when the user
     * explicitly taps "Add repository" ([integrateRepository]) is the official index
     * registered. Only metadata is loaded; no APK is ever installed automatically. A
     * refresh failure is not fatal and simply leaves suggestions empty.
     */
    suspend fun complete(
        preferences: ReaderPreferences,
        integrateRepository: Boolean = false,
    ): OnboardingState = mutex.withLock {
        val suggestions = if (integrateRepository) {
            extensions.addRepository(KEIYOUSHI_INDEX)
            try {
                extensions.refresh().filter { extension ->
                    (preferences.allowAdultContent || !extension.nsfw) &&
                        extension.sources.any { preferences.language.accepts(it.language) }
                }.sortedBy { it.packageName }.map { it.packageName }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val repository = if (integrateRepository) KEIYOUSHI_INDEX else null
        OnboardingState(preferences, repository, suggestions, completed = true).also { store.write(it) }
    }

    companion object {
        const val KEIYOUSHI_INDEX = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"
        /** Official website linked from the repository's own README. */
        const val KEIYOUSHI_WEBPAGE = "https://keiyoushi.github.io/"
        const val TURKISH_INDEX = "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/turkish/index.min.json"
        const val WESTERN_INDEX = "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/western/index.min.json"
    }
}
