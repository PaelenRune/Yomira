package com.yomira.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yomira.core.model.ReadingMode
import com.yomira.feature.settings.AppSettings
import com.yomira.feature.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [SettingsStore]. Unknown or corrupted values fall back to
 * the secure defaults instead of crashing, and [update] runs inside DataStore's
 * atomic edit so concurrent toggles can never lose a write.
 */
class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {
    override val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun read(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { values -> values.write(transform(values.toSettings())) }
    }

    private fun Preferences.toSettings() = AppSettings(
        vaultVisible = this[vaultVisible] ?: false,
        requireDeviceLock = this[deviceLock] ?: true,
        lockOnBackground = this[lockBackground] ?: true,
        readingMode = readingModes[this[readingMode]] ?: ReadingMode.MANGA_RTL,
        guidedView = this[guidedView] ?: false,
        bundledRepositoriesSeeded = this[bundledSeeded] ?: false,
    )

    private fun MutablePreferences.write(settings: AppSettings) {
        set(vaultVisible, settings.vaultVisible)
        set(deviceLock, settings.requireDeviceLock)
        set(lockBackground, settings.lockOnBackground)
        set(readingMode, settings.readingMode.name)
        set(guidedView, settings.guidedView)
        set(bundledSeeded, settings.bundledRepositoriesSeeded)
    }

    private companion object {
        val vaultVisible = booleanPreferencesKey("vaultVisible")
        val deviceLock = booleanPreferencesKey("requireDeviceLock")
        val lockBackground = booleanPreferencesKey("lockOnBackground")
        val readingMode = stringPreferencesKey("readingMode")
        val guidedView = booleanPreferencesKey("guidedView")
        val bundledSeeded = booleanPreferencesKey("bundledRepositoriesSeeded")
        val readingModes = ReadingMode.entries.associateBy { it.name }
    }
}
