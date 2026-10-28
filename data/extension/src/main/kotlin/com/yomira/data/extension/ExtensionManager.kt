package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.ExtensionUpdate
import com.yomira.domain.extension.InstalledExtension
import com.yomira.domain.extension.requireHttps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persist trusted repo URLs; implementing stores must make replacement atomic. */
interface RepositoryStore {
    suspend fun read(): List<String>
    suspend fun write(urls: List<String>)
}

fun interface RepositoryClient {
    suspend fun fetch(indexUrl: String): String
    suspend fun fetchPayload(indexUrl: String): RepositoryResponse = RepositoryResponse(fetch(indexUrl).toByteArray(Charsets.UTF_8))
}

data class RepositoryResponse(val bytes: ByteArray, val contentType: String? = null)

/** Supplied by the Android package manager and the user's explicit certificate trust decisions. */
fun interface InstalledExtensionProvider {
    suspend fun installed(): List<InstalledExtension>
}

class ExtensionManager(
    private val store: RepositoryStore,
    private val client: RepositoryClient,
    private val parser: KeiyoushiRepoParser,
    private val installedProvider: InstalledExtensionProvider,
) : ExtensionRepository {
    private val mutex = Mutex()
    private var snapshot: List<ExtensionDescriptor> = emptyList()
    private var origins: Map<String, String> = emptyMap()

    override suspend fun addRepository(indexUrl: String) = mutex.withLock {
        requireHttps(indexUrl)
        val urls = store.read()
        if (indexUrl !in urls) store.write(urls + indexUrl)
    }

    override suspend fun removeRepository(indexUrl: String) = mutex.withLock {
        store.write(store.read().filterNot { it == indexUrl })
        // Do not retain entries from a repository whose consent has been revoked.
        snapshot = emptyList()
        origins = emptyMap()
    }

    override suspend fun repositories(): List<String> = mutex.withLock {
        store.read().distinct()
    }

    /** All-or-nothing refresh preserves the previous snapshot on network or parsing failure. */
    override suspend fun refresh(): List<ExtensionDescriptor> = mutex.withLock {
        val indexed = store.read().distinct().map { url -> url to parser.parse(client.fetchPayload(url), url) }
        val entries = indexed.flatMap { it.second }
        // Ambiguous package ownership is not resolved by silently trusting the highest version.
        require(entries.map { it.packageName }.distinct().size == entries.size) {
            "Conflicting package names across repositories"
        }
        origins = indexed.flatMap { (url, descriptors) -> descriptors.map { it.packageName to url } }.toMap()
        entries.toList().also { snapshot = it }
    }

    override suspend fun available(): List<ExtensionDescriptor> = mutex.withLock {
        snapshot.toList()
    }

    override suspend fun installed(): List<InstalledExtension> = installedProvider.installed()

    override suspend fun repositoryUrl(packageName: String): String? = mutex.withLock { origins[packageName] }

    suspend fun updates(): List<ExtensionUpdate> {
        val available = available().associateBy { it.packageName }
        return installed().mapNotNull { installed ->
            available[installed.packageName]?.takeIf { it.versionCode > installed.versionCode }
                ?.let { ExtensionUpdate(installed, it) }
        }
    }
}
