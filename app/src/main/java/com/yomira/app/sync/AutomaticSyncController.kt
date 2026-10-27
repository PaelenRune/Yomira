package com.yomira.app.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.yomira.core.database.dao.SyncDataDao
import com.yomira.domain.sync.SyncScheduler
import com.yomira.domain.sync.CompletionSyncTracker
import com.yomira.feature.settings.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Application-scoped triggers, independent of the Settings screen's lifetime. */
@Singleton
class AutomaticSyncController @Inject constructor(
    private val settings: SettingsStore,
    private val scheduler: SyncScheduler,
    private val data: SyncDataDao,
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start(application: Application) {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            try {
                settings.settings.map { it.automaticSync }.distinctUntilChanged().collect { enabled ->
                    if (enabled) { scheduler.schedulePeriodic(); scheduler.syncNow() }
                    else { scheduler.cancelPeriodic(); scheduler.cancelImmediate() }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { scheduler.cancelPeriodic(); scheduler.cancelImmediate() }
        }
        scope.launch {
            val tracker = CompletionSyncTracker<String>()
            data.observeReaderProgress().collect { progress ->
                val completed = progress.filter { it.pageCount > 0 && it.pageIndex == it.pageCount - 1 }
                    .associate { it.chapterId to it.updatedAt }
                if (tracker.observe(completed)) trigger()
            }
        }
        scope.launch {
            val tracker = CompletionSyncTracker<Pair<String, String>>()
            data.observeReadChapters().collect { chapters ->
                val completed = chapters.associate { (it.mangaId to it.chapterId) to it.lastReadAt }
                if (tracker.observe(completed)) trigger()
            }
        }
    }

    private suspend fun trigger() {
        try { if (settings.read().automaticSync) scheduler.syncNow() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Preference failure never implies consent. */ }
    }

    override fun onActivityResumed(activity: Activity) { scope.launch { trigger() } }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
