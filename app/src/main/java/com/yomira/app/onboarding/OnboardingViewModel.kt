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
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val wizard: OnboardingWizardManager) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state = mutableState.asStateFlow()

    init { load() }

    fun load() {
        if (!mutableState.value.loading) mutableState.value = OnboardingUiState()
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { wizard.state() }
                mutableState.value = OnboardingUiState(loading = false, completed = saved?.completed == true,
                    adultEnabled = saved?.preferences?.allowAdultContent == true)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = OnboardingUiState(error = "Kurulum bilgisi okunamadı. Yeniden dene.")
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
                mutableState.value = OnboardingUiState(loading = false, completed = true, adultEnabled = adultEnabled)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(saving = false,
                    error = "Kurulum tamamlanamadı. Bağlantını kontrol edip tekrar dene veya deposuz başla.")
            }
        }
    }
}
