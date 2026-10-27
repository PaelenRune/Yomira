package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.ExtensionUpdate
import com.yomira.domain.extension.InstalledExtension
import com.yomira.domain.extension.requireHttps
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

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
    private var embeddedSnapshot = snapshot
    private var lastSyncNanos: Long? = null

    override suspend fun addRepository(indexUrl: String) = mutex.withLock {
        requireHttps(indexUrl)
        val urls = store.read()
        if (indexUrl !in urls) store.write(urls + indexUrl)
    }

    override suspend fun removeRepository(indexUrl: String) = mutex.withLock {
        require(embedded.none { it.indexUrl == indexUrl }) { "Bundled repositories cannot be removed" }
        store.write(store.read().filterNot { it == indexUrl })
        // Do not retain entries from a repository whose consent has been revoked.
        snapshot = embeddedSnapshot
    }

    override suspend fun repositories(): List<String> = mutex.withLock {
        (embedded.map { it.indexUrl } + store.read()).distinct()
    }

    /** All-or-nothing refresh preserves the previous snapshot on network or parsing failure. */
    override suspend fun refresh(): List<ExtensionDescriptor> = mutex.withLock {
        val refreshed = refreshedEmbedded()
        val entries = (refreshed + store.read().filterNot { url -> embedded.any { it.indexUrl == url } }
            .flatMap { url -> parser.parse(client.fetch(url), url) })
        // Ambiguous package ownership is not resolved by silently trusting the highest version.
        require(entries.map { it.packageName }.distinct().size == entries.size) {
            "Conflicting package names across repositories"
        }
        embeddedSnapshot = refreshed
        entries.toList().also { snapshot = it }
    }

    override suspend fun available(): List<ExtensionDescriptor> = mutex.withLock {
        // Lazy automatic sync on use; no detached scope/job survives the caller's lifecycle.
        val last = lastSyncNanos
        if (last == null || System.nanoTime() - last >= AUTO_SYNC_INTERVAL_NANOS) {
            val refreshed = refreshedEmbedded()
            val oldPackages = embeddedSnapshot.map { it.packageName }.toSet()
            val custom = snapshot.filterNot { it.packageName in oldPackages }
            if (custom.none { entry -> refreshed.any { it.packageName == entry.packageName } }) {
                embeddedSnapshot = refreshed
                snapshot = refreshed + custom
            }
        }
        snapshot.toList()
    }

    private suspend fun refreshedEmbedded(): List<ExtensionDescriptor> {
        lastSyncNanos = System.nanoTime()
        try {
            val upstream = embedded.mapNotNull { it.upstreamUrl }.distinct()
                .associateWith { client.fetch(it) }
            val refreshed = embedded.flatMap { repository ->
                val payload = repository.upstreamUrl?.let {
                    EmbeddedRepositories.refreshedPayload(repository, upstream.getValue(it))
                } ?: repository.payload
                parser.parse(payload, repository.indexUrl)
            }
            require(refreshed.map { it.packageName }.distinct().size == refreshed.size) {
                "Conflicting package names across bundled repositories"
            }
            return refreshed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Offline, malformed or incomplete upstream indexes must not replace the last valid snapshot.
            return embeddedSnapshot
        }
    }

    private companion object {
        const val AUTO_SYNC_INTERVAL_NANOS = 6L * 60 * 60 * 1_000_000_000
    }
    override suspend fun installed(): List<InstalledExtension> = installedProvider.installed()

    suspend fun updates(): List<ExtensionUpdate> {
        val available = available().associateBy { it.packageName }
        return installed().mapNotNull { installed ->
            available[installed.packageName]?.takeIf { it.versionCode > installed.versionCode }
                ?.let { ExtensionUpdate(installed, it) }
        }
    }
}
