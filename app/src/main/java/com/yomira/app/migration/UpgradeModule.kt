package com.yomira.app.migration

import android.content.Context
import com.yomira.app.BuildConfig
import com.yomira.data.extension.FileJsonSourceManifestStore
import com.yomira.data.extension.FileRepositoryStore
import com.yomira.data.extension.SandboxExtensionInstaller
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.OnboardingStore
import com.yomira.feature.settings.SettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

class PreferenceUpgradeState(context: Context) : UpgradeStateStore {
    private val preferences = context.getSharedPreferences("upgrade_cleanup", Context.MODE_PRIVATE)
    override fun lastSeenVersion(): Int? = if (preferences.contains("last_seen_version"))
        preferences.getInt("last_seen_version", 0) else null
    override fun noticePending() = preferences.getBoolean("notice_pending", false)
    override fun complete(version: Int, migrated: Boolean) {
        val edit = preferences.edit().putInt("last_seen_version", version)
        if (migrated) edit.putBoolean("sources_cleared", true).putBoolean("notice_pending", true)
        check(edit.commit()) { "Could not persist upgrade completion" }
    }
    override fun dismissNotice() {
        check(preferences.edit().putBoolean("notice_pending", false).commit())
    }
    fun sourcesCleared() = preferences.getBoolean("sources_cleared", false)
}

@Module
@InstallIn(SingletonComponent::class)
object UpgradeModule {
    @Provides @Singleton
    fun state(@ApplicationContext context: Context) = PreferenceUpgradeState(context)

    @Provides @Singleton
    fun cleanup(
        @ApplicationContext context: Context,
        state: PreferenceUpgradeState,
        manifests: FileJsonSourceManifestStore,
        installer: SandboxExtensionInstaller,
        repositories: ExtensionRepository,
        onboarding: OnboardingStore,
        settings: SettingsStore,
        syncExtensions: com.yomira.app.sync.DriveExtensionStore,
    ) = UpgradeCleanupManager(state, purgeSources = {
        UpgradeSourceCleanup(manifests, installer, repositories,
            FileRepositoryStore(context.filesDir.toPath().resolve("extensions/repositories.json")),
            onboarding, settings).purge()
        syncExtensions.revokeSourcesForUpgrade()
    }, upgradedLegacyInstall = {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    }, version = BuildConfig.VERSION_CODE)
}
