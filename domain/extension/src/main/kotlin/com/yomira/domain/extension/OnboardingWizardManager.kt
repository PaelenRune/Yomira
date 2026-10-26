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
     * Completes onboarding and always registers the bundled catalogs. Only repository
     * metadata is downloaded; no APK is silently installed or trusted. A refresh
     * failure is not fatal: registration is local, so the catalogs stay active and
     * suggestions are simply left empty until the next refresh.
     */
    suspend fun complete(
        preferences: ReaderPreferences,
        @Suppress("UNUSED_PARAMETER") integrateKeiyoushi: Boolean,
    ): OnboardingState = mutex.withLock {
        ONBOARDING_INDEXES.forEach { extensions.addRepository(it) }
        val suggestions = try {
            extensions.refresh().filter { extension ->
                (preferences.allowAdultContent || !extension.nsfw) &&
                    extension.sources.any { it.language == preferences.language.code || it.language == "all" }
            }.sortedBy { it.packageName }.map { it.packageName }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        OnboardingState(preferences, KEIYOUSHI_INDEX, suggestions, completed = true).also { store.write(it) }
    }

    companion object {
        const val KEIYOUSHI_INDEX = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"
        const val TURKISH_INDEX = "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/turkish/index.min.json"
        const val WESTERN_INDEX = "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/western/index.min.json"
        val ONBOARDING_INDEXES = listOf(KEIYOUSHI_INDEX, TURKISH_INDEX, WESTERN_INDEX)
    }
}
