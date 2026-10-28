package com.yomira.app.migration

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface UpgradeStateStore {
    fun lastSeenVersion(): Int?
    fun noticePending(): Boolean
    fun complete(version: Int, migrated: Boolean)
    fun dismissNotice()
}

/** The pending notice remains visible through onboarding and waits for repository dialogs. */
fun shouldShowUpgradeNotice(pending: Boolean, onboardingLoading: Boolean, repoDialogVisible: Boolean): Boolean =
    pending && !onboardingLoading && !repoDialogVisible

/** The version is committed only after every cleanup succeeds; interrupted work is retried. */
class UpgradeCleanupManager(
    private val state: UpgradeStateStore,
    private val purgeSources: suspend () -> Unit,
    private val upgradedLegacyInstall: () -> Boolean,
    private val version: Int = 21,
) {
    private val mutex = Mutex()

    suspend fun run() = mutex.withLock {
        val previous = state.lastSeenVersion()
        if (previous != null && previous >= version) return@withLock
        // Builds before 21 never wrote a marker. Package installation timestamps distinguish
        // their upgrades from a genuinely new install without inspecting unrelated user data.
        val migrate = (previous != null && previous < 21) ||
            (previous == null && upgradedLegacyInstall())
        if (migrate) purgeSources()
        state.complete(version, migrate)
    }

    fun noticePending() = state.noticePending()
    fun dismissNotice() = state.dismissNotice()
}
