package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JSON-first extension installation, the deliberate replacement for `pm install`.
 *
 * Flow for one Keiyoushi/external-repo APK:
 * 1. the APK is downloaded through [ExtensionDownloadClient] (HTTPS, size-capped);
 * 2. [ApkSandboxExtractor] lifts `classes*.dex` into the app-private sandbox, hashes them
 *    and scans the DEX string table for `<Package>Extension` entry classes;
 * 3. a [JsonSourceManifest] — a few KB of portable JSON holding every scrap of metadata
 *    and the scraping identity (source ids + dex hashes) — becomes the installed record.
 *
 * The Android framework never learns the file existed: no broadcast, no permission prompt,
 * no entry in the app drawer, and Drive only ever syncs the manifests. Execution happens
 * later inside the isolated runtime via a `DexClassLoader` pointed at
 * [SandboxIntegrityGuard.LoadableSandbox.dexPaths] after the hashes were re-checked.
 */
class SandboxExtensionInstaller(
    private val layout: SandboxLayout,
    private val store: FileJsonSourceManifestStore,
    private val download: ExtensionDownloadClient,
    private val extractor: ApkSandboxExtractor = ApkSandboxExtractor(layout),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /**
     * Installs (or upgrades) one repository entry. Throws when the archive carries no usable
     * dex, when the URL is not HTTPS or when the sandbox rejects the payload; a failed
     * install never leaves a manifest behind, so the UI's "installed" state stays truthful.
     */
    suspend fun install(descriptor: ExtensionDescriptor, repositoryUrl: String): JsonSourceManifest =
        mutex.withLock {
            require(descriptor.packageName.isNotBlank()) { "Descriptor needs a package name" }
            val manifest = store.find(descriptor.packageName)
            require(manifest == null || manifest.versionCode < descriptor.versionCode) {
                "Package ${descriptor.packageName} is already installed at version ${manifest?.versionCode}"
            }
            val apkBytes = download.download(descriptor.apkUrl)
            val candidate = RuntimeApkToJsonConverter(extractor).convert(apkBytes, descriptor, repositoryUrl, clock())
            candidate.validate()
            try {
                store.save(candidate)
            } catch (error: Exception) {
                // Roll the staged dex back so disk and manifests cannot diverge.
                store.delete(descriptor.packageName)
                throw error
            }
            candidate
        }

    /** Drops the manifest first (the visible truth), then the machine-local dex cache. */
    suspend fun uninstall(packageName: String): Boolean = mutex.withLock { store.delete(packageName) }

    /**
     * Re-materialises the dex for a manifest that arrived from Google Drive on a fresh
     * device: the APK is re-downloaded from the recorded URL and the extracted hashes must
     * match the cloud manifest exactly, otherwise nothing is trusted and the sandbox stays
     * empty.
     */
    suspend fun rehydrate(manifest: JsonSourceManifest): SandboxIntegrityGuard.LoadableSandbox? = mutex.withLock {
        require(manifest.dexFiles.isNotEmpty()) { "Manifest carries no dex fingerprints" }
        val apkBytes = download.download(manifest.apkUrl)
        val extraction = try {
            extractor.extract(apkBytes, manifest.packageName, clock(), manifest.versionCode)
        } catch (_: IllegalArgumentException) {
            store.delete(manifest.packageName)
            return@withLock null
        }
        val expected = manifest.dexFiles.sortedBy { it.name }
        val actual = extraction.dexFiles.sortedBy { it.name }
        if (expected != actual) {
            // The APK behind the recorded URL no longer matches the cloud fingerprint:
            // refuse it entirely and wipe the half-written sandbox.
            store.delete(manifest.packageName)
            return@withLock null
        }
        store.save(manifest.normalized())
        SandboxIntegrityGuard(layout, store).requireLoadable(manifest.packageName)
    }
}
