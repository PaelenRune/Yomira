package com.yomira.data.extension

import com.yomira.domain.extension.InstalledExtension
import com.yomira.domain.extension.requireHttps
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The single source of truth for JSON-first extensions.
 *
 * One [JsonSourceManifest] per package under [SandboxLayout.manifestDirectory], written
 * atomically. This directory — and only this directory — is the Google-Drive-syncable part
 * of an extension installation; dex payloads stay machine-local.
 */
class FileJsonSourceManifestStore(private val layout: SandboxLayout) {
    private val mutex = Mutex()

    suspend fun save(manifest: JsonSourceManifest) = mutex.withLock {
        val text = JsonSourceManifestCodec.encode(manifest.normalized())
        val file = layout.manifestFile(manifest.packageName)
        withContext(Dispatchers.IO) {
            Files.createDirectories(file.parent)
            val temporary = Files.createTempFile(file.parent, ".yomira-manifest-", ".json")
            try {
                temporary.toFile().outputStream().use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                Unit
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    suspend fun find(packageName: String): JsonSourceManifest? = mutex.withLock {
        val file = layout.manifestFile(packageName)
        withContext(Dispatchers.IO) {
            if (!Files.exists(file)) return@withContext null
            JsonSourceManifestCodec.decode(Files.readString(file, Charsets.UTF_8))
        }
    }

    suspend fun all(): List<JsonSourceManifest> = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!Files.exists(layout.manifestDirectory)) return@withContext emptyList()
            Files.list(layout.manifestDirectory).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".json") }
                    .sorted(Comparator.naturalOrder())
                    .map { JsonSourceManifestCodec.decode(Files.readString(it, Charsets.UTF_8)) }
                    .toList()
            }
        }
    }

    suspend fun delete(packageName: String): Boolean = mutex.withLock {
        layout.deleteDex(packageName)
        withContext(Dispatchers.IO) { Files.deleteIfExists(layout.manifestFile(packageName)) }
    }

    /** Compact Drive-syncable view of every manifest, sorted and deterministic. */
    suspend fun exportIndex(): String = JsonSourceManifestCodec.encodeIndex(all())

    /**
     * Merges a Drive-synced index into this device. A manifest for a package already present
     * wins when it was installed earlier or carries a higher version code, so a phone never
     * clobbers a newer tablet installation with a stale cloud copy. Returns how many
     * manifests were written.
     */
    suspend fun importIndex(index: String, replaceExisting: Boolean = false): Int {
        val incoming = JsonSourceManifestCodec.decodeIndex(index)
        var written = 0
        for (manifest in incoming) {
            requireHttps(manifest.apkUrl)
            val existing = find(manifest.packageName)
            val shouldWrite = replaceExisting || existing == null ||
                manifest.versionCode > existing.versionCode ||
                (manifest.versionCode == existing.versionCode && manifest.installedAt < existing.installedAt)
            if (shouldWrite) {
                save(manifest)
                written++
            }
        }
        return written
    }

    /** Writes [exportIndex] next to the manifests for a Drive sync adapter to pick up. */
    suspend fun writeIndexFile(): Path {
        val text = exportIndex()
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Files.createDirectories(layout.indexFile.parent)
                val temporary = Files.createTempFile(layout.indexFile.parent, ".yomira-index-", ".json")
                try {
                    temporary.toFile().outputStream().use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                        stream.fd.sync()
                    }
                    Files.move(temporary, layout.indexFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    Unit
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
        }
        return layout.indexFile
    }
}

/**
 * JSON-first replacement for the Android package manager as the source of "installed" state:
 * a manifest on disk *is* the installation. Trust is explicit — manifests only appear through
 * [SandboxExtensionInstaller], which the user triggers against a consented repository.
 */
class ManifestInstalledExtensionProvider(private val store: FileJsonSourceManifestStore) : InstalledExtensionProvider {
    override suspend fun installed(): List<InstalledExtension> =
        store.all().map { InstalledExtension(it.packageName, it.versionCode, trusted = true, descriptor = it.descriptor()) }
}

/**
 * Verifies, on every sandbox load, that the dex bytes on disk still match the manifest that
 * the user consented to. The isolated runtime (DexClassLoader host) must call
 * [requireLoadable] before `new DexClassLoader(...)`, so a tampered private directory is
 * refused even though it is on app-owned storage.
 */
class SandboxIntegrityGuard(
    private val layout: SandboxLayout,
    private val store: FileJsonSourceManifestStore,
) {
    data class LoadableSandbox(val manifest: JsonSourceManifest, val dexPaths: List<Path>)

    suspend fun requireLoadable(packageName: String): LoadableSandbox {
        val manifest = store.find(packageName) ?: throw SecurityException("No sandbox manifest for $packageName")
        manifest.validate()
        val directory = layout.dexDirectory(packageName)
        val paths = manifest.dexFiles.map { entry ->
            val path = directory.resolve(entry.name)
            if (!Files.isRegularFile(path)) throw SecurityException("Dex ${entry.name} is missing from the sandbox")
            val actual = withContext(Dispatchers.IO) {
                ApkSandboxExtractor.sha256Hex(Files.readAllBytes(path))
            }
            if (actual != entry.sha256 || Files.size(path) != entry.sizeBytes) {
                throw SecurityException("Dex ${entry.name} no longer matches its manifest hash")
            }
            path
        }
        return LoadableSandbox(manifest, paths)
    }
}

/** Downloads a repository APK into memory for [SandboxExtensionInstaller]; never installs it. */
fun interface ExtensionDownloadClient {
    suspend fun download(apkUrl: String): ByteArray
}

/**
 * Streams the APK over HTTPS with a hard byte cap, mirroring [OkHttpRepositoryClient]'s
 * redirect and timeout discipline. The bytes go straight into [ApkSandboxExtractor].
 */
class OkHttpExtensionDownloadClient(
    client: okhttp3.OkHttpClient,
    private val maxApkBytes: Long = DEFAULT_MAX_APK_BYTES,
) : ExtensionDownloadClient {
    init { require(maxApkBytes > 0) { "APK cap must be positive" } }

    private val client = client.newBuilder()
        .followSslRedirects(false)
        .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun download(apkUrl: String): ByteArray {
        requireHttps(apkUrl)
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = client.newCall(okhttp3.Request.Builder().url(apkUrl).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val result = runCatching {
                        response.use {
                            requireHttps(it.request.url.toString())
                            if (!it.isSuccessful) throw IOException("APK HTTP ${it.code}")
                            val body = it.body ?: throw IOException("Empty APK response")
                            if (body.contentLength() > maxApkBytes) throw IOException("APK too large")
                            val source = body.source()
                            if (!source.request(maxApkBytes + 1)) throw IOException("APK too large")
                            source.readByteArray(minOf(body.contentLength().coerceAtLeast(0), maxApkBytes))
                        }
                    }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
    }

    private companion object { const val DEFAULT_MAX_APK_BYTES = 128L * 1024 * 1024 }
}
