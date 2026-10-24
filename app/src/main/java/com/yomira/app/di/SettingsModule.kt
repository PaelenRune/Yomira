package com.yomira.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.yomira.app.settings.DataStoreSettingsStore
import com.yomira.feature.settings.AppCacheCleaner
import com.yomira.feature.settings.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore =
        DataStoreSettingsStore(context.settingsDataStore)

    @Provides @Singleton
    fun cacheCleaner(@ApplicationContext context: Context): AppCacheCleaner =
        AppCacheCleaner { clearAppCache(context) }
}

/**
 * Deletes every expendable file under the internal and external cache
 * directories and reports how many bytes were freed. Room databases and
 * DataStore files live elsewhere, so library and settings survive.
 */
private suspend fun clearAppCache(context: Context): Long = withContext(Dispatchers.IO) {
    var freed = 0L
    listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { directory ->
        directory.walkBottomUp().forEach { file -> if (file.isFile) freed += file.length() }
        directory.deleteRecursively()
    }
    freed
}
