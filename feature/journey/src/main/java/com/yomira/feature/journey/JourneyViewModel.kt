package com.yomira.feature.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.domain.journey.JourneyDashboard
import com.yomira.domain.journey.JourneyRepository
import com.yomira.domain.journey.WrappedPeriod
import com.yomira.domain.journey.WrappedSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val dashboard: JourneyDashboard? = null,
    val error: String? = null,
)

data class WrappedUiState(
    val period: WrappedPeriod = WrappedPeriod.MONTHLY,
    val loading: Boolean = true,
    val summary: WrappedSummary? = null,
    val error: String? = null,
)

@HiltViewModel
class JourneyViewModel @Inject constructor(
    private val repository: JourneyRepository,
) : ViewModel() {
    val profileState = repository.observeDashboard()
        .map { ProfileUiState(loading = false, dashboard = it) }
        .catch { error ->
            emit(ProfileUiState(loading = false, error = error.message))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(),
        )

    private val _wrapped = MutableStateFlow(WrappedUiState())
    val wrapped = _wrapped.asStateFlow()
    private var wrappedJob: Job? = null

    init {
        refresh()
    }

    /** Called by the screen when it becomes visible. */
    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.refreshAchievements() }
        }
    }

    fun loadWrapped(period: WrappedPeriod = _wrapped.value.period) {
        wrappedJob?.cancel()
        _wrapped.value = _wrapped.value.copy(period = period, loading = true, error = null)
        wrappedJob = viewModelScope.launch {
            runCatching { repository.wrapped(period) }
                .onSuccess { summary ->
                    _wrapped.value = WrappedUiState(period = period, loading = false, summary = summary)
                }
                .onFailure { error ->
                    _wrapped.value = WrappedUiState(period = period, loading = false, error = error.message)
                }
        }
    }
}
