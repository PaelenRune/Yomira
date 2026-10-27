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
}

/** Supplied by the Android package manager and the user's explicit certificate trust decisions. */
fun interface InstalledExtensionProvider {
    suspend fun installed(): List<InstalledExtension>
}

class ExtensionManager(
    private val store: RepositoryStore,
    private val client: RepositoryClient,
    private val parser: KeiyoushiRepoParser,
    private val installedProvider: InstalledExtensionProvider,
    private val embedded: List<EmbeddedRepository> = EmbeddedRepositories.load(),
) : ExtensionRepository {
    private val mutex = Mutex()
    private var snapshot: List<ExtensionDescriptor> = embedded.flatMap { parser.parse(it.payload, it.indexUrl) }

    override suspend fun addRepository(indexUrl: String) = mutex.withLock {
        requireHttps(indexUrl)
        val urls = store.read()
        if (indexUrl !in urls) store.write(urls + indexUrl)
    }

    override suspend fun removeRepository(indexUrl: String) = mutex.withLock {
        require(embedded.none { it.indexUrl == indexUrl }) { "Bundled repositories cannot be removed" }
        store.write(store.read().filterNot { it == indexUrl })
        // Do not retain entries from a repository whose consent has been revoked.
        snapshot = embedded.flatMap { parser.parse(it.payload, it.indexUrl) }
    }

    override suspend fun repositories(): List<String> = mutex.withLock {
        (embedded.map { it.indexUrl } + store.read()).distinct()
    }

    /** All-or-nothing refresh preserves the previous snapshot on network or parsing failure. */
    override suspend fun refresh(): List<ExtensionDescriptor> = mutex.withLock {
        // Embedded indexes are authoritative offline snapshots. Custom URLs remain explicit network inputs.
        val entries = (embedded.flatMap { parser.parse(it.payload, it.indexUrl) } +
            store.read().filterNot { url -> embedded.any { it.indexUrl == url } }
                .flatMap { url -> parser.parse(client.fetch(url), url) })
        // Ambiguous package ownership is not resolved by silently trusting the highest version.
        require(entries.map { it.packageName }.distinct().size == entries.size) {
            "Conflicting package names across repositories"
        }
        entries.toList().also { snapshot = it }
    }

    override suspend fun available(): List<ExtensionDescriptor> = mutex.withLock { snapshot.toList() }
    override suspend fun installed(): List<InstalledExtension> = installedProvider.installed()

    suspend fun updates(): List<ExtensionUpdate> {
        val available = available().associateBy { it.packageName }
        return installed().mapNotNull { installed ->
            available[installed.packageName]?.takeIf { it.versionCode > installed.versionCode }
                ?.let { ExtensionUpdate(installed, it) }
        }
    }
}
