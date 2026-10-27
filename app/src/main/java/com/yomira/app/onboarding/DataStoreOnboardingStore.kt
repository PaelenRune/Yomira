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
import kotlinx.coroutines.flow.map

/** Device-local consent must not be overwritten by cloud preference sync. */
class DataStoreOnboardingStore(private val dataStore: DataStore<Preferences>) : OnboardingStore {
    override suspend fun read(): OnboardingState? {
        return decode(dataStore.data.first())
    }

    override fun observe() = dataStore.data.map(::decode)

    private fun decode(values: Preferences): OnboardingState? {
        if (values[completed] != true) return null
        return OnboardingState(
            preferences = ReaderPreferences(
                ReadingLanguage.valueOf(requireNotNull(values[language])),
                values[adult] ?: false,
                values[isAdult] ?: false,
                values[excludedTags].orEmpty(),
                values[favoriteGenres].orEmpty(),
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
            values[isAdult] = state.preferences.isAdult
            values[excludedTags] = state.preferences.excludedTags
            values[favoriteGenres] = state.preferences.favoriteGenres
            state.repositoryUrl?.let { values[repository] = it } ?: values.remove(repository)
            values[packages] = state.suggestedPackages.toSet()
            values[completed] = state.completed
        }
    }

    private companion object {
        val completed = booleanPreferencesKey("onboardingCompleted")
        val language = stringPreferencesKey("readingLanguage")
        val adult = booleanPreferencesKey("adultEnabled")
        val isAdult = booleanPreferencesKey("readerIsAdult")
        val excludedTags = stringSetPreferencesKey("excludedTags")
        val favoriteGenres = stringSetPreferencesKey("favoriteGenres")
        val repository = stringPreferencesKey("repositoryUrl")
        val packages = stringSetPreferencesKey("suggestedPackages")
    }
}
