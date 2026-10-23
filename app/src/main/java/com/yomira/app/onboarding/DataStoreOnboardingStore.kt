package com.yomira.app.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import kotlinx.coroutines.flow.first

/** Device-local consent must not be overwritten by cloud preference sync. */
class DataStoreOnboardingStore(private val dataStore: DataStore<Preferences>) : OnboardingStore {
    override suspend fun read(): OnboardingState? {
        val values = dataStore.data.first()
        if (values[completed] != true) return null
        return OnboardingState(
            preferences = ReaderPreferences(
                ReadingLanguage.valueOf(requireNotNull(values[language])),
                values[adult] ?: false,
            ),
            repositoryUrl = values[repository],
            suggestedPackages = values[packages].orEmpty().sorted(),
            completed = true,
        )
    }

    override suspend fun write(state: OnboardingState) {
        dataStore.edit { values ->
            values[language] = state.preferences.language.name
            values[adult] = state.preferences.allowAdultContent
            state.repositoryUrl?.let { values[repository] = it } ?: values.remove(repository)
            values[packages] = state.suggestedPackages.toSet()
            values[completed] = state.completed
        }
    }

    private companion object {
        val completed = booleanPreferencesKey("onboardingCompleted")
        val language = stringPreferencesKey("readingLanguage")
        val adult = booleanPreferencesKey("adultEnabled")
        val repository = stringPreferencesKey("repositoryUrl")
        val packages = stringSetPreferencesKey("suggestedPackages")
    }
}
