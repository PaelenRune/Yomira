package com.yomira.app.di

import android.content.Context
import android.app.ActivityManager
import androidx.datastore.preferences.core.edit
import com.yomira.app.settings.CompleteFactoryReset
import com.yomira.app.settings.FactoryReset
import com.yomira.core.database.YomiraDatabase
import androidx.datastore.preferences.preferencesDataStore
import coil.imageLoader
import com.yomira.app.settings.CacheManager
import com.yomira.app.settings.DataStoreSettingsStore
import com.yomira.app.settings.DefaultSettingsMaintenanceServices
import com.yomira.app.settings.SettingsMaintenanceServices
import com.yomira.app.settings.ManagedCache
import com.yomira.app.settings.TemporaryDirectoryCache
import com.yomira.data.extension.SandboxLayout
import com.yomira.feature.settings.AppCacheCleaner
import com.yomira.feature.settings.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import okhttp3.OkHttpClient

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides @Singleton
    fun factoryReset(@ApplicationContext context: Context, database: YomiraDatabase, cache: CacheManager): FactoryReset =
        CompleteFactoryReset(
            clearDatabase = { database.clearAllTables() },
            clearSettings = { context.settingsDataStore.edit { it.clear() }; Unit },
            clearOnboarding = { context.onboardingDataStore.edit { it.clear() }; Unit },
            clearCaches = { cache.clearAllCache().getOrThrow() },
            clearApplicationUserData = { context.getSystemService(ActivityManager::class.java).clearApplicationUserData() },
        )

    @Provides
    fun maintenanceServices(services: DefaultSettingsMaintenanceServices): SettingsMaintenanceServices = services

    @Provides @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore =
        DataStoreSettingsStore(context.settingsDataStore)

    @Provides @Singleton
    fun extensionConsentStore(@ApplicationContext context: Context): com.yomira.data.extension.ExtensionConsentStore =
        com.yomira.app.settings.DataStoreExtensionConsentStore(context.settingsDataStore)

    @Provides @Singleton
    fun cacheManager(@ApplicationContext context: Context, client: OkHttpClient, layout: SandboxLayout): CacheManager {
        // Resolve Coil lazily inside CacheManager's IO context, not on Hilt's caller thread.
        val images = object : ManagedCache {
            override suspend fun sizeBytes(): Long = context.imageLoader.let {
                (it.memoryCache?.size?.toLong() ?: 0L) + (it.diskCache?.size ?: 0L)
            }

            override suspend fun clear() {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            }
        }
        val network = object : ManagedCache {
            override suspend fun sizeBytes(): Long = client.cache?.size() ?: 0L
            override suspend fun clear() { client.cache?.evictAll() }
        }
        val directories = TemporaryDirectoryCache(
            directories = { listOfNotNull(context.cacheDir, context.externalCacheDir).map { it.toPath() } },
            excludedDirectories = {
                listOfNotNull(
                    context.imageLoader.diskCache?.directory?.toFile()?.toPath(),
                    client.cache?.directory?.toPath(),
                )
            },
        )
        // The extractor shares this lock: never delete staging during an active extraction.
        // Installed package dex and manifests are deliberately outside this allowlist.
        val extraction = TemporaryDirectoryCache(directories = {
            if (!Files.isDirectory(layout.dexRoot, NOFOLLOW_LINKS)) emptyList()
            else Files.newDirectoryStream(layout.dexRoot, ".extracting-*").use { entries -> entries.toList() }
        })
        val temporary = object : ManagedCache {
            override suspend fun sizeBytes(): Long = directories.sizeBytes() +
                layout.withExtractionLock { extraction.sizeBytes() }

            override suspend fun clear() {
                directories.clear()
                layout.withExtractionLock { extraction.clear() }
            }
        }
        return CacheManager(images, network, temporary)
    }

    @Provides @Singleton
    fun cacheCleaner(manager: CacheManager): AppCacheCleaner =
        AppCacheCleaner { manager.clearAndGetFreedBytes() }
}
