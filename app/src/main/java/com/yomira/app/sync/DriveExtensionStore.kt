package com.yomira.app.sync

import android.content.Context
import android.util.AtomicFile
import com.yomira.data.extension.FileJsonSourceManifestStore
import com.yomira.data.extension.JsonSourceManifestCodec
import com.yomira.data.extension.SandboxExtensionInstaller
import com.yomira.data.sync.InstalledExtensionSyncEntry
import com.yomira.data.sync.SyncPayload
import com.yomira.data.sync.SyncPayloadCodec
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.sync.SyncExtensionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Portable configuration stays separate from executable, machine-local extension files. */
@Singleton
class DriveExtensionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val manifests: FileJsonSourceManifestStore,
    private val installer: SandboxExtensionInstaller,
    private val repositories: ExtensionRepository,
    private val codec: SyncPayloadCodec,
) : SyncExtensionStore {
    private val file = AtomicFile(File(context.filesDir, "sync/extensions.json"))
    private val localState = context.getSharedPreferences("sync-extension-materialization", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun read(): List<InstalledExtensionSyncEntry> = mutex.withLock {
        val entries = snapshot()
        persist(entries)
        recordMaterialized(manifests.all().map { it.packageName }.toSet())
        entries
    }

    private suspend fun snapshot(): List<InstalledExtensionSyncEntry> {
        val saved = withContext(Dispatchers.IO) {
            try { codec.decode(file.readFully().toString(Charsets.UTF_8)).extensions }
            catch (_: java.io.FileNotFoundException) { emptyList() }
        }.associateBy { it.packageId }.toMutableMap()
        val local = manifests.all()
        val installedIds = local.map { it.packageName }.toSet()
        localState.getStringSet("materialized", emptySet()).orEmpty().forEach { packageId ->
            val previous = saved[packageId]
            if (packageId !in installedIds && previous?.installed == true) {
                saved[packageId] = previous.copy(installed = false, updatedAt = System.currentTimeMillis())
            }
        }
        local.forEach { manifest ->
            val previous = saved[manifest.packageName]
            if (previous == null || manifest.installedAt > previous.updatedAt) {
                saved[manifest.packageName] = InstalledExtensionSyncEntry(
                    manifest.packageName, manifest.versionCode, listOf(manifest.repositoryUrl),
                    previous?.customConfigs.orEmpty(), manifest.installedAt,
                    manifestJson = JsonSourceManifestCodec.encode(manifest))
            }
        }
        return saved.values.toList()
    }

    /** Called by an extension configuration editor; configs are portable preferences, never OAuth tokens. */
    suspend fun updateConfiguration(packageId: String, configs: Map<String, String>) = mutex.withLock {
        val entries = snapshot().map { entry ->
            if (entry.packageId == packageId) entry.copy(customConfigs = configs.toSortedMap(), updatedAt = System.currentTimeMillis())
            else entry
        }
        require(entries.any { it.packageId == packageId }) { "Unknown extension: $packageId" }
        persist(entries)
    }

    private suspend fun persist(entries: List<InstalledExtensionSyncEntry>) = withContext(Dispatchers.IO) {
        val bytes = codec.encode(SyncPayload(deviceId = "extension-metadata", extensions = entries))
            .toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    private suspend fun recordMaterialized(packages: Set<String>) = withContext(Dispatchers.IO) {
        check(localState.edit().putStringSet("materialized", packages).commit()) {
            "Could not persist extension sync state"
        }
    }

    override suspend fun write(entries: List<InstalledExtensionSyncEntry>) = mutex.withLock {
        val merged = snapshot().associateBy { it.packageId }.toMutableMap()
        entries.forEach { incoming ->
            val existing = merged[incoming.packageId]
            if (existing == null || incoming.updatedAt >= existing.updatedAt) merged[incoming.packageId] = incoming
        }
        // Persist pending records first so failed downloads never lose the user's configuration.
        persist(merged.values.toList())
        merged.values.forEach { entry ->
            val local = manifests.find(entry.packageId)
            if (local != null && local.installedAt > entry.updatedAt) return@forEach
            if (!entry.installed) {
                if (local != null) installer.uninstall(entry.packageId)
            } else if (local == null || local.versionCode != entry.versionCode) {
                val manifest = entry.manifestJson?.let(JsonSourceManifestCodec::decode) ?: return@forEach
                require(manifest.packageName == entry.packageId && manifest.versionCode == entry.versionCode)
                require(manifest.repositoryUrl in entry.repositorySources)
                // Remote data cannot grant trust to a repository this device hasn't consented to.
                if (manifest.repositoryUrl !in repositories.repositories()) return@forEach
                // A failed rehydration may remove staged files. That is not a user uninstall.
                recordMaterialized(localState.getStringSet("materialized", emptySet()).orEmpty() - entry.packageId)
                if (installer.rehydrate(manifest) == null) throw IOException("Extension fingerprint mismatch: ${entry.packageId}")
            }
        }
        recordMaterialized(manifests.all().map { it.packageName }.toSet())
    }
}
