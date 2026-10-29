package com.yomira.app.metadata

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataBackfillScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager get() = WorkManager.getInstance(context)
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val work get() = manager.getWorkInfosForUniqueWorkFlow(NAME)

    @Synchronized fun scheduleOnce() {
        if (prefs.getBoolean("scheduled", false)) return
        enqueue()
        prefs.edit().putBoolean("scheduled", true).apply()
    }

    /** Manual retry also revisits previously failed records; concurrent requests are kept unique. */
    fun start() {
        val active = manager.getWorkInfosForUniqueWork(NAME).get().any { !it.state.isFinished }
        if (active) return
        prefs.edit().remove("processed").remove("failures").remove("complete").commit()
        enqueue()
    }

    fun cancel() { manager.cancelUniqueWork(NAME) }

    private fun enqueue() {
        manager.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MetadataBackfillWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, java.util.concurrent.TimeUnit.SECONDS).build())
    }

    companion object {
        internal const val PREFS = "metadata-backfill-821"
        private const val NAME = "metadata-backfill-821"
    }
}
