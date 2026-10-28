package com.yomira.app.migration

import com.yomira.app.catalog.NativeSourceRepository
import com.yomira.data.extension.*
import com.yomira.domain.extension.*
import com.yomira.feature.settings.AppSettings
import com.yomira.feature.settings.SettingsStore
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpgradeSourceCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `purge removes all manifests dex repos and seeds while preserving unrelated bytes and preferences`() = runTest {
        val root = temporary.root.toPath()
        val layout = SandboxLayout(root.resolve("sandbox"))
        val manifests = FileJsonSourceManifestStore(layout)
        val installer = SandboxExtensionInstaller(layout, manifests, ExtensionDownloadClient { error("No downloads") })
        for (name in listOf("site.one", "site.two")) {
            manifests.save(JsonSourceManifest(packageName = name, name = name, language = "en", nsfw = false,
                versionName = "1", versionCode = 1, apkUrl = "https://example.org/a.apk",
                repositoryUrl = "https://example.org/index.json", installedAt = 1,
                dexFiles = listOf(SandboxedDexFile("classes.dex", 1, "0".repeat(64)))))
            Files.createDirectories(layout.dexDirectory(name))
            Files.write(layout.dexDirectory(name).resolve("classes.dex"), byteArrayOf(1))
        }
        val repoPath = root.resolve("repositories.json")
        val store = FileRepositoryStore(repoPath)
        store.write(store.read() + "https://custom.example.org/index.json")
        val repository = ExtensionManager(store, RepositoryClient { error("No network") }, KeiyoushiRepoParser(),
            ManifestInstalledExtensionProvider(manifests))
        val onboarding = FileOnboardingStore(root.resolve("onboarding.json"))
        val beforeOnboarding = OnboardingState(ReaderPreferences(ReadingLanguage.TURKISH, true, true,
            setOf("NTR"), setOf("Action")), "https://custom.example.org/index.json", listOf("site.one"), true)
        onboarding.write(beforeOnboarding)
        val settings = MemorySettings(AppSettings(vaultVisible = true, automaticSync = true,
            guidedView = true, bundledRepositoriesSeeded = true, stealthShieldEnabled = false))
        val beforeSettings = settings.read()
        // These stores are intentionally outside the cleanup's capability boundary.
        val protected = listOf("library.db", "vault.db", "history.db", "achievements.json", "yomira_ui.xml")
            .associateWith { "private-$it".toByteArray() }
        protected.forEach { (name, bytes) -> Files.write(root.resolve(name), bytes) }
        val cleanup = UpgradeSourceCleanup(manifests, installer, repository, store, onboarding, settings)
        cleanup.purge()
        cleanup.purge()
        assertTrue(manifests.all().isEmpty())
        assertTrue(repository.installed().isEmpty())
        assertTrue(repository.repositories().isEmpty())
        assertTrue(FileRepositoryStore(repoPath).read().isEmpty())
        for (name in listOf("site.one", "site.two")) assertFalse(Files.exists(layout.dexDirectory(name)))
        assertEquals(beforeOnboarding.copy(repositoryUrl = null, suggestedPackages = emptyList()), onboarding.read())
        assertEquals(beforeSettings.copy(bundledRepositoriesSeeded = false, repositoriesUserManaged = true), settings.read())
        protected.forEach { (name, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(root.resolve(name))) }
        assertTrue(NativeSourceRepository(repository, includeNative = { false }).installed().isEmpty())
        assertEquals(1, NativeSourceRepository(repository).installed().size) // fresh-install default unchanged
    }

    private class MemorySettings(initial: AppSettings) : SettingsStore {
        override val settings = MutableStateFlow(initial)
        override suspend fun read() = settings.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }
}
