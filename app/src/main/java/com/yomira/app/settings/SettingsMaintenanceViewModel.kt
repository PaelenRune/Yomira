package com.yomira.app.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.ReadingLanguage
import com.yomira.domain.extension.ReaderPreferences
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
    private val reset: FactoryReset,
    private val auth: DriveAccountAuth,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsMaintenanceState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.email.collect { email ->
                mutableState.update { it.copy(driveAccountEmail = email, driveSignInRequired = email == null) }
                if (email == null) {
                    try { store.update { it.copy(automaticSync = false) }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED) }
                }
            }
        }
        viewModelScope.launch {
            try {
                onboarding.observe().collect { saved ->
                    mutableState.update { it.copy(readerPreferences = saved?.preferences, contentLanguage = saved?.preferences?.language) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED) }
        }
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
                mutableState.update { it.copy(contentLanguage = saved?.preferences?.language, readerPreferences = saved?.preferences) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED) }
        }
    }

    fun clearCache() {
        if (state.value.cacheBusy || state.value.resetBusy) return
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
        if (state.value.resetBusy || state.value.driveAuthBusy) return
        if (enabled && auth.email.value == null) { message(MaintenanceMessage.SIGN_IN_REQUIRED); return }
        viewModelScope.launch {
            try {
                store.update { it.copy(automaticSync = enabled) }
                if (enabled) syncNow()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED) }
        }
    }

    fun syncNow() {
        if (state.value.syncBusy || state.value.resetBusy || state.value.driveAuthBusy) return
        if (auth.email.value == null) { message(MaintenanceMessage.SIGN_IN_REQUIRED); return }
        mutableState.update { it.copy(syncBusy = true, message = null) }
        viewModelScope.launch {
            try {
                when (val result = services.sync()) {
                    is SyncResult.Success -> {
                        mutableState.update { it.copy(lastSyncedAt = result.syncedAt, driveSignInRequired = false) }
                        message(MaintenanceMessage.SYNC_SUCCESS)
                    }
                    SyncResult.NotSignedIn -> {
                        auth.clearConnection()
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
        if (state.value.preferenceBusy || state.value.resetBusy) return
        mutableState.update { it.copy(preferenceBusy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val saved = checkNotNull(onboarding.read())
                    onboarding.write(saved.copy(preferences = saved.preferences.copy(language = language)))
                }
                mutableState.update { it.copy(contentLanguage = language, readerPreferences = it.readerPreferences?.copy(language = language)) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED)
            } finally { mutableState.update { it.copy(preferenceBusy = false) } }
        }
    }

    fun consumeMessage() { mutableState.update { it.copy(message = null) } }

    fun beginDriveSignIn(): Intent? {
        if (state.value.run { driveAuthBusy || syncBusy || resetBusy }) return null
        return try {
            auth.signInIntent().also { mutableState.update { it.copy(driveAuthBusy = true, message = null) } }
        } catch (_: Exception) {
            message(MaintenanceMessage.AUTH_FAILED)
            null
        }
    }

    fun completeDriveSignIn(data: Intent?, cancelled: Boolean = false) {
        try {
            if (!cancelled && !auth.completeSignIn(data)) message(MaintenanceMessage.AUTH_FAILED)
        } catch (error: com.google.android.gms.common.api.ApiException) {
            if (error.statusCode != com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                message(MaintenanceMessage.AUTH_FAILED)
            }
        } catch (_: Exception) {
            message(MaintenanceMessage.AUTH_FAILED)
        } finally { mutableState.update { it.copy(driveAuthBusy = false) } }
    }

    fun driveSignInLaunchFailed() {
        mutableState.update { it.copy(driveAuthBusy = false, message = MaintenanceMessage.AUTH_FAILED) }
    }

    fun disconnectDrive() {
        if (state.value.run { driveAuthBusy || syncBusy || resetBusy }) return
        mutableState.update { it.copy(driveAuthBusy = true, message = null) }
        viewModelScope.launch {
            try {
                store.update { it.copy(automaticSync = false) }
                auth.disconnect { success ->
                    mutableState.update { it.copy(driveAuthBusy = false,
                        message = if (success) null else MaintenanceMessage.DISCONNECT_FAILED) }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(driveAuthBusy = false, message = MaintenanceMessage.DISCONNECT_FAILED) }
            }
        }
    }

    fun setReaderPreferences(preferences: ReaderPreferences) {
        if (state.value.preferenceBusy || state.value.resetBusy) return
        mutableState.update { it.copy(preferenceBusy = true, message = null) }
        viewModelScope.launch {
            try {
                val safe = preferences.copy(allowAdultContent = preferences.isAdult && preferences.allowAdultContent)
                withContext(Dispatchers.IO) {
                    val saved = checkNotNull(onboarding.read())
                    onboarding.write(saved.copy(preferences = safe))
                }
                mutableState.update { it.copy(readerPreferences = safe, contentLanguage = safe.language) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message(MaintenanceMessage.PREFERENCE_FAILED)
            } finally { mutableState.update { it.copy(preferenceBusy = false) } }
        }
    }

    fun factoryReset() {
        if (state.value.run { resetBusy || cacheBusy || syncBusy || preferenceBusy || driveAuthBusy }) return
        mutableState.update { it.copy(resetBusy = true, message = null) }
        viewModelScope.launch {
            try {
                reset.reset()
                // Keep the UI blocked until Android stops the process. Relaunch loads onboarding.
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(resetBusy = false, message = MaintenanceMessage.RESET_FAILED) }
            }
        }
    }

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
