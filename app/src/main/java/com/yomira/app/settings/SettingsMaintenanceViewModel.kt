package com.yomira.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.ReadingLanguage
import com.yomira.domain.sync.SyncResult
import com.yomira.feature.settings.MaintenanceMessage
import com.yomira.feature.settings.SettingsMaintenanceState
import com.yomira.feature.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Side effects stay outside Compose; cache owners and Drive remain the sources of truth. */
@HiltViewModel
class SettingsMaintenanceViewModel @Inject constructor(
    private val services: SettingsMaintenanceServices,
    private val store: SettingsStore,
    private val onboarding: OnboardingStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsMaintenanceState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                services.lastSyncedAt.collect { timestamp ->
                    mutableState.update { it.copy(lastSyncedAt = timestamp) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.SYNC_FAILED) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { refreshCacheSize() }
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { onboarding.read() }
                mutableState.update { it.copy(contentLanguage = saved?.preferences?.language) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED) }
        }
    }

    fun clearCache() {
        if (state.value.cacheBusy) return
        mutableState.update { it.copy(cacheBusy = true, message = null) }
        viewModelScope.launch {
            try {
                services.clearCache()
                message(MaintenanceMessage.CACHE_CLEARED)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.CACHE_FAILED)
            } finally {
                refreshCacheSize()
                mutableState.update { it.copy(cacheBusy = false) }
            }
        }
    }

    fun setAutomaticSync(enabled: Boolean) {
        viewModelScope.launch {
            try {
                store.update { it.copy(automaticSync = enabled) }
                if (enabled) syncNow()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED) }
        }
    }

    fun syncNow() {
        if (state.value.syncBusy) return
        mutableState.update { it.copy(syncBusy = true, message = null) }
        viewModelScope.launch {
            try {
                when (val result = services.sync()) {
                    is SyncResult.Success -> {
                        mutableState.update { it.copy(lastSyncedAt = result.syncedAt, driveSignInRequired = false) }
                        message(MaintenanceMessage.SYNC_SUCCESS)
                    }
                    SyncResult.NotSignedIn -> {
                        mutableState.update { it.copy(driveSignInRequired = true) }
                        message(MaintenanceMessage.SIGN_IN_REQUIRED)
                    }
                    is SyncResult.Failure -> message(MaintenanceMessage.SYNC_FAILED)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.SYNC_FAILED)
            } finally { mutableState.update { it.copy(syncBusy = false) } }
        }
    }

    fun setContentLanguage(language: ReadingLanguage) {
        if (state.value.preferenceBusy) return
        mutableState.update { it.copy(preferenceBusy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val saved = checkNotNull(onboarding.read())
                    onboarding.write(saved.copy(preferences = saved.preferences.copy(language = language)))
                }
                mutableState.update { it.copy(contentLanguage = language) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED)
            } finally { mutableState.update { it.copy(preferenceBusy = false) } }
        }
    }

    fun consumeMessage() { mutableState.update { it.copy(message = null) } }

    private suspend fun refreshCacheSize() {
        try {
            val bytes = services.cacheSize()
            mutableState.update { it.copy(cacheBytes = bytes, cacheSizeFailed = false) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(cacheSizeFailed = true) }
            message(MaintenanceMessage.CACHE_SIZE_FAILED)
        }
    }

    private fun message(value: MaintenanceMessage) { mutableState.update { it.copy(message = value) } }
}
