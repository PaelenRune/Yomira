package com.yomira.domain.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Silent periodic sync. Runs only when the network is available, never blocks the UI
 * and lets WorkManager apply exponential backoff with the system's battery policy.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val preference: AutomaticSyncPreference,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!preference.enabled()) return Result.success(workDataOf(KEY_SKIPPED to true))
        return when (val result = syncManager.sync()) {
            is SyncResult.Success -> Result.success(workDataOf(KEY_SYNCED_AT to result.syncedAt))

        // Not being signed in is a stable user state, not a transient failure.
            SyncResult.NotSignedIn -> Result.success(workDataOf(KEY_SKIPPED to true))

            is SyncResult.Failure -> {
                if (result.retryable && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to result.message.take(MAX_ERROR_LENGTH)))
                }
            }
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "yomira-sync-periodic"
        const val UNIQUE_IMMEDIATE_WORK_NAME = "yomira-sync-immediate"
        const val KEY_SYNCED_AT = "syncedAt"
        const val KEY_SKIPPED = "skipped"
        const val KEY_ERROR = "error"

        private const val MAX_RETRY_ATTEMPTS = 5
        private const val MAX_ERROR_LENGTH = 1024
    }
}
