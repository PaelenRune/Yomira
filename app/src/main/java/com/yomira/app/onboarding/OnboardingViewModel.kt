package com.yomira.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingUiState(
    val loading: Boolean = true,
    val completed: Boolean = false,
    val saving: Boolean = false,
    val adultEnabled: Boolean = false,
    /**
     * Reading language code ("tr"/"en") of the saved or just-completed setup. The host
     * mirrors it into the UI locale via `LocaleManagerCompat`, so choosing English here
     * switches every app string instantly.
     */
    val languageCode: String? = null,
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val wizard: OnboardingWizardManager) : ViewModel() {
    /** Stable error codes; the host maps them to localized strings (English/Turkish). */
    companion object {
        const val ERROR_LOAD = "onboarding-load-failed"
        const val ERROR_SAVE = "onboarding-save-failed"
    }

    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state = mutableState.asStateFlow()

    init { load() }

    fun load() {
        if (!mutableState.value.loading) mutableState.value = OnboardingUiState()
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { wizard.state() }
                mutableState.value = OnboardingUiState(loading = false, completed = saved?.completed == true,
                    adultEnabled = saved?.preferences?.allowAdultContent == true,
                    languageCode = saved?.preferences?.language?.code)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = OnboardingUiState(error = ERROR_LOAD)
            }
        }
    }

    fun complete(languageCode: String, adultEnabled: Boolean, integrateRepository: Boolean) {
        if (mutableState.value.loading || mutableState.value.saving || mutableState.value.completed) return
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val language = ReadingLanguage.entries.first { it.code == languageCode }
                withContext(Dispatchers.IO) {
                    wizard.complete(ReaderPreferences(language, adultEnabled), integrateRepository)
                }
                mutableState.value = OnboardingUiState(loading = false, completed = true,
                    adultEnabled = adultEnabled, languageCode = language.code)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(saving = false, error = ERROR_SAVE)
            }
        }
    }
}
