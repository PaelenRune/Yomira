package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionSource
import com.yomira.domain.extension.requireHttps
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The portable, human-readable identity of one sandboxed extension.
 *
 * Yomira never hands extension APKs to the Android package installer. The repository APK is
 * downloaded once, its `classes*.dex` payloads are extracted into the app-private sandbox
 * ([SandboxLayout]) and loaded through a `DexClassLoader` at runtime, and everything worth
 * keeping — metadata, scraping identity (source ids route every call inside the dex) and a
 * SHA-256 fingerprint of each extracted dex — is stored here as a few KB of JSON.
 *
 * Consequences this format exists for:
 * - Android shows no "install unknown app" warning and the launcher drawer stays clean,
 *   because nothing is ever registered with `pm install`.
 * - Google Drive only ever sees these JSON files, never the megabyte-sized binaries; the
 *   binaries are re-downloaded from [apkUrl] and verified against [dexFiles] on a new device.
 *
 * Field names are the on-disk contract of `manifest.json` and `sandbox_index.json`;
 * keep them stable and bump [CURRENT_SCHEMA_VERSION] only additively.
 */
@Serializable
data class JsonSourceManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val packageName: String,
    val name: String,
    val language: String,
    val nsfw: Boolean,
    val versionName: String,
    val versionCode: Long,
    /** HTTPS location the dex payloads can be re-fetched from; never blank. */
    val apkUrl: String,
    /** Consent-checked repository index that advertised the package. */
    val repositoryUrl: String,
    /** UTC epoch milliseconds of the sandbox install that produced this manifest. */
    val installedAt: Long,
    /** Runtime engine expected to host the dex. Reserved for future scraper engines. */
    val runtimeEngine: String = DEX_CLASSLOADER_RUNTIME,
    /** Scraping rules ship compiled inside the dex; the hashes pin them down exactly. */
    val dexFiles: List<SandboxedDexFile>,
    /** Entry classes discovered in the dex string table (best-effort `…Extension` names). */
    val entryClasses: List<String> = emptyList(),
    /** Sources this package provides; the ids are the routing keys the dex code expects. */
    val sources: List<ManifestSourceEntry> = emptyList(),
) {
    init {
        require(packageName.isNotBlank() && name.isNotBlank() && language.isNotBlank()) {
            "Manifest requires package name, display name and language"
        }
        require(versionCode > 0) { "versionCode must be positive" }
        require(versionName.isNotBlank()) { "versionName must not be blank" }
        require(installedAt >= 0L) { "installedAt must not be negative" }
        require(runtimeEngine.isNotBlank()) { "runtimeEngine must not be blank" }
        require(dexFiles.isNotEmpty()) { "A sandboxed extension needs at least one dex payload" }
        require(dexFiles.map { it.name }.distinct().size == dexFiles.size) { "Duplicate dex entries" }
        dexFiles.forEach { entry ->
            require(entry.sizeBytes > 0L) { "Empty dex entry ${entry.name}" }
            require(entry.sha256.matches(SHA256_HEX)) { "dex ${entry.name} needs a lowercase SHA-256 hex digest" }
        }
        require(sources.map { it.id }.distinct().size == sources.size) { "Duplicate source ids" }
        require(entryClasses.map { it.trim() }.filter { it.isNotEmpty() }.size == entryClasses.size) {
            "Entry class names must be trimmed and non-blank"
        }
    }

    /** Rejects documents that could not have been produced by this app version. */
    fun validate() {
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) { "Unsupported manifest schema $schemaVersion" }
        requireHttps(apkUrl)
        requireHttps(repositoryUrl)
        sources.forEach { source ->
            require(source.id.isNotBlank() && source.name.isNotBlank() && source.language.isNotBlank()) {
                "Manifest source entries need id, name and language"
            }
        }
    }

    /** Canonical ordering keeps exported Drive indexes byte-identical across devices. */
    fun normalized(): JsonSourceManifest = copy(
        dexFiles = dexFiles.sortedBy { it.name },
        entryClasses = entryClasses.sorted(),
        sources = sources.sortedBy { it.id },
    )

    fun descriptor(): ExtensionDescriptor = ExtensionDescriptor(
        packageName = packageName,
        name = name,
        language = language,
        nsfw = nsfw,
        versionName = versionName,
        versionCode = versionCode,
        apkUrl = apkUrl,
        sources = sources.map { ExtensionSource(it.id, it.name, it.language) },
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
        const val DEX_CLASSLOADER_RUNTIME: String = "dex-classloader"
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        /** Maps one repository-index entry plus a completed extraction onto a manifest. */
        fun of(
            descriptor: ExtensionDescriptor,
            repositoryUrl: String,
            installedAt: Long,
            dexFiles: List<SandboxedDexFile>,
            entryClasses: List<String> = emptyList(),
        ): JsonSourceManifest = JsonSourceManifest(
            packageName = descriptor.packageName,
            name = descriptor.name,
            language = descriptor.language,
            nsfw = descriptor.nsfw,
            versionName = descriptor.versionName,
            versionCode = descriptor.versionCode,
            apkUrl = descriptor.apkUrl,
            repositoryUrl = repositoryUrl,
            installedAt = installedAt,
            dexFiles = dexFiles,
            entryClasses = entryClasses.filter { it.isNotBlank() }.distinct().sorted(),
            sources = descriptor.sources.map { ManifestSourceEntry(it.id, it.name, it.language) },
        ).normalized()
    }
}

/** One extracted `classes*.dex` with the hash needed to prove it was never swapped. */
@Serializable
data class SandboxedDexFile(val name: String, val sizeBytes: Long, val sha256: String)

/** One routable source inside the package; mirrors [ExtensionSource] in JSON form. */
@Serializable
data class ManifestSourceEntry(val id: String, val name: String, val language: String)

/** Strict codec shared by the store, the Drive export and the tests. */
object JsonSourceManifestCodec {
    val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(manifest: JsonSourceManifest): String {
        manifest.validate()
        return json.encodeToString(JsonSourceManifest.serializer(), manifest.normalized())
    }

    fun decode(text: String): JsonSourceManifest =
        json.decodeFromString(JsonSourceManifest.serializer(), text).also { it.validate() }

    /** Portable Drive index: every manifest in one small JSON array, sorted by package. */
    fun encodeIndex(manifests: List<JsonSourceManifest>): String {
        require(manifests.map { it.packageName }.distinct().size == manifests.size) { "Duplicate packages in index" }
        val sorted = manifests.sortedBy { it.packageName }
        sorted.forEach { it.validate() }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(JsonSourceManifest.serializer()),
            sorted.map { it.normalized() },
        )
    }

    fun decodeIndex(text: String): List<JsonSourceManifest> =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(JsonSourceManifest.serializer()), text)
            .also { loaded ->
                loaded.forEach { it.validate() }
                require(loaded.map { it.packageName }.distinct().size == loaded.size) { "Duplicate packages in index" }
            }
}
