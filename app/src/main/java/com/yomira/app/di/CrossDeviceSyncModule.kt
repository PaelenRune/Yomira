package com.yomira.app.di

import com.yomira.app.sync.DriveExtensionStore
import com.yomira.domain.sync.AutomaticSyncPreference
import com.yomira.domain.sync.SyncExtensionStore
import com.yomira.feature.settings.SettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CrossDeviceSyncModule {
    @Binds abstract fun extensions(store: DriveExtensionStore): SyncExtensionStore

    companion object {
        @Provides fun automaticSyncPreference(settings: SettingsStore): AutomaticSyncPreference =
            AutomaticSyncPreference { settings.read().automaticSync }
    }
}
