package com.yomira.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.domain.extension.FavoriteSeries
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
    /**
     * Saved reading language ("tr", "en", or "both"). The host mirrors Turkish into
     * the Turkish UI locale; English and "both" use the English UI locale.
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
                    languageCode = saved?.preferences?.language?.code)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = OnboardingUiState(error = ERROR_LOAD)
            }
        }
    }

    fun complete(languageCode: String, integrateRepository: Boolean) {
        save(languageCode, integrateRepository, false, emptySet(), emptySet())
    }

    fun complete(languageCode: String, isAdult: Boolean, excludedTags: Set<String>, favoriteGenres: Set<String>, favoriteSeries: List<FavoriteSeries> = emptyList()) {
        save(languageCode, true, isAdult, excludedTags, favoriteGenres, favoriteSeries)
    }

    private fun save(languageCode: String, integrateRepository: Boolean, isAdult: Boolean, excludedTags: Set<String>, favoriteGenres: Set<String>, favoriteSeries: List<FavoriteSeries> = emptyList()) {
        if (mutableState.value.loading || mutableState.value.saving || mutableState.value.completed) return
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val language = ReadingLanguage.entries.first { it.code == languageCode }
                withContext(Dispatchers.IO) {
                    wizard.complete(ReaderPreferences(language, isAdult, isAdult, excludedTags, favoriteGenres, favoriteSeries), integrateRepository)
                }
                mutableState.value = OnboardingUiState(loading = false, completed = true,
                    languageCode = language.code)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(saving = false, error = ERROR_SAVE)
            }
        }
    }
}
