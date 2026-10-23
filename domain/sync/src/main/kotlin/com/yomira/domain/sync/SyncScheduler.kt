package com.yomira.domain.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules the two common entry points: periodic background sync and "sync now". */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic(intervalHours: Long = DEFAULT_INTERVAL_HOURS) {
        require(intervalHours >= MIN_INTERVAL_HOURS) {
            "WorkManager requires at least $MIN_INTERVAL_HOURS hours for a periodic sync"
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC_WORK_NAME,
            // KEEP preserves the existing schedule and its next run time across app starts.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            SyncWorker.UNIQUE_IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    private companion object {
        const val DEFAULT_INTERVAL_HOURS = 6L
        const val MIN_INTERVAL_HOURS = 6L
    }
}
