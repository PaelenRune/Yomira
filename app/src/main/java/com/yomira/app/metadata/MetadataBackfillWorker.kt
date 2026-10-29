package com.yomira.app.metadata

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yomira.app.R
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.SyncDataDao
import com.yomira.data.metadata.WaterfallMetadataAggregator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull

@HiltWorker
class MetadataBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val manga: MangaDao,
    private val library: SyncDataDao,
    private val metadata: WaterfallMetadataAggregator,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(MetadataBackfillScheduler.PREFS, Context.MODE_PRIVATE)
        val done = prefs.getStringSet("processed", emptySet()).orEmpty().toMutableSet()
        val ids = library.getAllLibraryEntries().filter { it.inLibrary }.map { it.mangaId }
        var failures = prefs.getInt("failures", 0)
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            applicationContext.getString(R.string.metadata_backfill_title), NotificationManager.IMPORTANCE_LOW))
        suspend fun progress() {
            val count = ids.count { it in done }
            setProgress(workDataOf("done" to count, "total" to ids.size, "failures" to failures))
            // No titles or artwork on the lock screen, even when the library includes Vault entries.
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(applicationContext.getString(R.string.metadata_backfill_title))
                .setContentText("$count / ${ids.size}")
                .setProgress(ids.size, count, false).setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .addAction(0, applicationContext.getString(R.string.metadata_backfill_cancel),
                    androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
                .build()
            try { notifications.notify(NOTIFICATION, notification) } catch (_: SecurityException) { /* UI progress remains available. */ }
        }
        return try {
            progress()
            val runner = LibraryMetadataBackfill(
                allowed = { !isStopped && permitted(applicationContext) },
                refresh = { key ->
                    val old = manga.getById(key)
                    if (old == null) true else {
                        val fresh = withTimeoutOrNull(30_000) { metadata.aggregate(old.title, forceRefresh = true) }
                        if (fresh == null || !permitted(applicationContext)) false else {
                            manga.updateDisplayMetadata(key, fresh.title,
                                (old.altTitles + old.title + fresh.altTitles).filterNot { it.equals(fresh.title, true) }.distinct(),
                                fresh.coverUrl)
                            true
                        }
                    }
                },
                checkpoint = { key, success ->
                    done += key
                    if (!success) failures++
                    check(prefs.edit().putStringSet("processed", done.toSet()).putInt("failures", failures).commit())
                },
                pause = { progress(); kotlinx.coroutines.delay(2_000) },
            )
            if (!runner.run(ids.filterNot { it in done })) Result.retry()
            else {
                check(prefs.edit().putBoolean("complete", true).commit())
                Result.success(workDataOf("done" to ids.size, "total" to ids.size, "failures" to failures))
            }
        } finally { notifications.cancel(NOTIFICATION) }
    }

    companion object {
        private const val CHANNEL = "metadata-backfill"
        private const val NOTIFICATION = 821
        internal fun permitted(context: Context): Boolean =
            !context.getSystemService(KeyguardManager::class.java).isDeviceLocked &&
                context.getSystemService(PowerManager::class.java).isInteractive &&
                context.getSystemService(ConnectivityManager::class.java).let {
                    !it.isActiveNetworkMetered && it.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                }
    }
}
