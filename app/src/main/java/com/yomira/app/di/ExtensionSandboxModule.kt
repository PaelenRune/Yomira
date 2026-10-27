package com.yomira.app.di

import android.content.Context
import com.yomira.app.backup.RoomBackupImportTarget
import com.yomira.app.extensions.ExtensionOperations
import com.yomira.app.extensions.SandboxExtensionOperations
import com.yomira.data.extension.FileJsonSourceManifestStore
import com.yomira.data.extension.InstalledExtensionProvider
import com.yomira.data.extension.ManifestInstalledExtensionProvider
import com.yomira.data.extension.OkHttpExtensionDownloadClient
import com.yomira.data.extension.SandboxExtensionInstaller
import com.yomira.data.extension.SandboxIntegrityGuard
import com.yomira.data.extension.SandboxLayout
import com.yomira.data.extension.TachiyomiBackupImporter
import com.yomira.data.extension.TachiyomiBackupParser
import com.yomira.domain.extension.BackupImportTarget
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * JSON-first extension engine wiring.
 *
 * The sandbox lives on app-private storage (`getDir` → `no_backup`-style private dir): dex
 * files never go through the package installer, so Android raises no "unknown sources"
 * prompt; the only files a Drive adapter ever uploads are the JSON manifests below.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExtensionSandboxModule {
    @Provides
    fun provideExtensionOperations(operations: SandboxExtensionOperations): ExtensionOperations = operations

    @Provides
    @Singleton
    fun provideSandboxLayout(@ApplicationContext context: Context): SandboxLayout =
        SandboxLayout(context.getDir("extension_sandbox", Context.MODE_PRIVATE).toPath())

    @Provides
    @Singleton
    fun provideManifestStore(layout: SandboxLayout): FileJsonSourceManifestStore = FileJsonSourceManifestStore(layout)

    /** "Installed" is the manifest store, not PackageManager — no APK installs, no warnings. */
    @Provides
    @Singleton
    fun provideInstalledExtensionProvider(store: FileJsonSourceManifestStore): InstalledExtensionProvider =
        ManifestInstalledExtensionProvider(store)

    @Provides
    @Singleton
    fun provideExtensionInstaller(
        layout: SandboxLayout,
        store: FileJsonSourceManifestStore,
        client: OkHttpClient,
    ): SandboxExtensionInstaller = SandboxExtensionInstaller(
        layout = layout,
        store = store,
        download = OkHttpExtensionDownloadClient(client),
    )

    /** The isolated DexClassLoader runtime host must gate every load through this guard. */
    @Provides
    @Singleton
    fun provideIntegrityGuard(
        layout: SandboxLayout,
        store: FileJsonSourceManifestStore,
    ): SandboxIntegrityGuard = SandboxIntegrityGuard(layout, store)
}

/** Tachiyomi / Mihon `.tachibk` and JSON backup import wiring. */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    @Singleton
    fun provideBackupParser(): TachiyomiBackupParser = TachiyomiBackupParser()

    @Provides
    @Singleton
    fun provideBackupImporter(parser: TachiyomiBackupParser): TachiyomiBackupImporter =
        TachiyomiBackupImporter(parser)
}

/** Binds the Room-backed import destination used by [BackupModule]'s importer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupImportBindingModule {
    @Binds
    @Singleton
    abstract fun bindBackupImportTarget(impl: RoomBackupImportTarget): BackupImportTarget
}
