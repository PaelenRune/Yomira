package com.yomira.app.migration

import com.yomira.data.extension.FileJsonSourceManifestStore
import com.yomira.data.extension.RepositoryStore
import com.yomira.data.extension.SandboxExtensionInstaller
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.OnboardingStore
import com.yomira.feature.settings.SettingsStore

/** Deliberately has no access to Room, history, achievements, library or vault stores. */
class UpgradeSourceCleanup(
    private val manifests: FileJsonSourceManifestStore,
    private val installer: SandboxExtensionInstaller,
    private val repositories: ExtensionRepository,
    private val repositoryStore: RepositoryStore,
    private val onboarding: OnboardingStore,
    private val settings: SettingsStore,
) {
    suspend fun purge() {
        manifests.all().forEach { installer.uninstall(it.packageName) }
        manifests.writeIndexFile()
        repositories.repositories().forEach { repositories.removeRepository(it) }
        // Persist [] even if the store was absent (whose defaults are bundled URLs).
        repositoryStore.write(emptyList())
        onboarding.read()?.let { onboarding.write(it.copy(repositoryUrl = null, suggestedPackages = emptyList())) }
        settings.update { it.copy(bundledRepositoriesSeeded = false, repositoriesUserManaged = true) }
    }
}
