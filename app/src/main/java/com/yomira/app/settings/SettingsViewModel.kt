package com.yomira.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.app.update.AppUpdateChecker
import com.yomira.core.model.ReadingMode
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.requireHttps
import com.yomira.feature.settings.AppCacheCleaner
import com.yomira.feature.settings.AppSettings
import com.yomira.feature.settings.CuratedRepository
import com.yomira.feature.settings.SettingsStore
import com.yomira.feature.settings.SettingsUiState
import com.yomira.feature.settings.UpdateCheckStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns every settings mutation. Facts flow out through [state] and [settings];
 * the composable maps them to localized copy, so this class stays
 * language-agnostic and unit-testable on the JVM.
 *
 * Revealing the +18 Vault is deliberately NOT offered here as a plain setter:
 * the caller must first verify the device credential and then forward the
 * verdict to [onVaultRevealResult]. Hiding, in contrast, is instant — a panic
 * action must never wait for a prompt.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val extensions: ExtensionRepository,
    private val cacheCleaner: AppCacheCleaner,
    private val updateChecker: AppUpdateChecker,
) : ViewModel() {

    /** Eager so non-compose readers (Activity.onStop) always see the persisted truth. */
    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val repositories = MutableStateFlow(emptyList<String>())
    private val busyRepository = MutableStateFlow<String?>(null)
    private val updateStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    private val feedback = MutableStateFlow(SettingsFeedback())

    val state: StateFlow<SettingsUiState> = combine(
        settings, repositories, busyRepository, updateStatus, feedback,
    ) { settings, repositories, busy, update, feedback ->
        SettingsUiState(
            settings = settings,
            repositories = repositories,
            busyRepository = busy,
            updateCheck = update,
            cacheClearedBytes = feedback.cacheClearedBytes,
            cacheFailed = feedback.cacheFailed,
            repositoryError = feedback.repositoryError,
            invalidRepositoryUrl = feedback.invalidRepositoryUrl,
            revealDenied = feedback.revealDenied,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            refreshRepositoriesNow()
        }
    }

    fun refreshRepositories() {
        viewModelScope.launch { refreshRepositoriesNow() }
    }

    private suspend fun refreshRepositoriesNow() {
        try {
            repositories.value = extensions.repositories()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A store read failure leaves the previous snapshot; toggles still surface errors.
        }
    }

    /** Instant panic hide. Revealing requires the device credential; see [onVaultRevealResult]. */
    fun setVaultVisible(visible: Boolean) {
        if (visible) return
        updateSettings { it.copy(vaultVisible = false) }
    }

    /** Applies the verdict of the device-credential flow that guards revealing the Vault. */
    fun onVaultRevealResult(verified: Boolean) {
        if (verified) {
            feedback.value = feedback.value.copy(revealDenied = false)
            updateSettings { it.copy(vaultVisible = true) }
        } else {
            feedback.value = feedback.value.copy(revealDenied = true)
        }
    }

    fun setRequireDeviceLock(enabled: Boolean) = updateSettings { it.copy(requireDeviceLock = enabled) }

    fun setStealthShield(enabled: Boolean) = updateSettings { it.copy(stealthShieldEnabled = enabled) }

    fun setLockOnBackground(enabled: Boolean) = updateSettings { it.copy(lockOnBackground = enabled) }

    fun setReadingMode(mode: ReadingMode) = updateSettings { it.copy(readingMode = mode) }

    /** Default state of Guided View when a chapter opens. */
    fun setGuidedView(enabled: Boolean) = updateSettings { it.copy(guidedView = enabled) }

    /** Registers or revokes a curated index; registration persists even when the refresh is offline. */
    fun setRepositoryEnabled(repository: CuratedRepository, enabled: Boolean) {
        toggleRepository(repository.indexUrl, enabled)
    }

    fun addCustomRepository(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return
        viewModelScope.launch {
            busyRepository.value = url
            feedback.value = feedback.value.copy(repositoryError = false, invalidRepositoryUrl = false)
            try {
                requireHttps(url)
                extensions.addRepository(url)
                refreshBestEffort()
                repositories.value = extensions.repositories()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback.value = feedback.value.copy(invalidRepositoryUrl = true)
            } finally {
                busyRepository.value = null
            }
        }
    }

    fun removeRepository(indexUrl: String) {
        toggleRepository(indexUrl, enabled = false)
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                val freed = cacheCleaner.clear()
                feedback.value = feedback.value.copy(cacheClearedBytes = freed, cacheFailed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback.value = feedback.value.copy(cacheFailed = true)
            }
        }
    }

    fun checkUpdates() {
        if (updateStatus.value == UpdateCheckStatus.Checking) return
        viewModelScope.launch {
            updateStatus.value = UpdateCheckStatus.Checking
            // The checker collapses every failure to null by design: a manual
            // check must never surface a scary error, only "up to date".
            updateStatus.value = try {
                val info = updateChecker.check()
                if (info == null) UpdateCheckStatus.UpToDate
                else UpdateCheckStatus.Available(info.version, info.apkDownloadUrl, info.releaseNotes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                UpdateCheckStatus.UpToDate
            }
        }
    }

    private fun toggleRepository(indexUrl: String, enabled: Boolean) {
        if (busyRepository.value != null) return
        viewModelScope.launch {
            busyRepository.value = indexUrl
            feedback.value = feedback.value.copy(repositoryError = false, invalidRepositoryUrl = false)
            try {
                if (enabled) {
                    extensions.addRepository(indexUrl)
                    refreshBestEffort()
                } else {
                    extensions.removeRepository(indexUrl)
                }
                repositories.value = extensions.repositories()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback.value = feedback.value.copy(repositoryError = true)
            } finally {
                busyRepository.value = null
            }
        }
    }

    /** Registration is the success criterion; fetching the index is best-effort. */
    private suspend fun refreshBestEffort() {
        try {
            extensions.refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                store.update(transform)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // DataStore writes are local; a failed toggle simply keeps the previous state.
            }
        }
    }

    /** Transient one-shot flags; cleared by the next action of the same kind. */
    private data class SettingsFeedback(
        val cacheClearedBytes: Long? = null,
        val cacheFailed: Boolean = false,
        val repositoryError: Boolean = false,
        val invalidRepositoryUrl: Boolean = false,
        val revealDenied: Boolean = false,
    )
}
