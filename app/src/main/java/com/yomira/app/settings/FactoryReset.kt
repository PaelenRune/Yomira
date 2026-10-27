package com.yomira.app.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

fun interface FactoryReset {
    suspend fun reset()
}

/**
 * Clear live storage through its owners, then let Android remove ALL remaining app data
 * (extensions, shared preferences, identities, jobs and in-memory state) and stop the process.
 * Once confirmed, finishing the wipe must not depend on the settings screen's lifecycle.
 */
class CompleteFactoryReset(
    private val clearDatabase: suspend () -> Unit,
    private val clearSettings: suspend () -> Unit,
    private val clearOnboarding: suspend () -> Unit,
    private val clearCaches: suspend () -> Unit,
    private val clearApplicationUserData: () -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FactoryReset {
    override suspend fun reset() = withContext(ioDispatcher + NonCancellable) {
        val failures = mutableListOf<Exception>()
        for (clear in listOf(clearCaches, clearDatabase, clearSettings, clearOnboarding)) {
            try {
                clear()
            } catch (error: Exception) {
                // Android's authoritative wipe can still succeed if an individual owner fails.
                failures += error
            }
        }
        try {
            check(clearApplicationUserData()) { "Android refused to clear application data" }
        } catch (error: Exception) {
            failures.forEach(error::addSuppressed)
            throw error
        }
    }
}
