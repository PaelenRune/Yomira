package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.ExtensionUpdate
import com.yomira.domain.extension.InstalledExtension
import com.yomira.domain.extension.requireHttps
import kotlinx.coroutines.CancellationException
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
    private val embedded: List<EmbeddedRepository> = EmbeddedRepositories.load(),
) : ExtensionRepository {
    private val mutex = Mutex()

    /** Curated indexes parse offline at construction, so the catalog is never empty on a fresh install. */
    private val embeddedEntries: Map<String, List<ExtensionDescriptor>> =
        embedded.associate { it.indexUrl to parser.parse(it.payload, it.indexUrl) }

    private var snapshot: List<ExtensionDescriptor> = emptyList()
    private var origins: Map<String, String> = emptyMap()
    private var seeded = false

    /** Seeds the curated indexes whose URL is present in the store; on a fresh install those are the defaults. */
    private suspend fun seedLocked() {
        if (seeded) return
        seeded = true
        val registered = store.read().distinct()
        val merged = mergeIndexed(embeddedEntries.filterKeys { it in registered }.toList())
        snapshot = merged.map { it.second }
        origins = merged.associate { (url, descriptor) -> descriptor.packageName to url }
    }

    override suspend fun addRepository(indexUrl: String) = mutex.withLock {
        requireHttps(indexUrl)
        val urls = store.read()
        if (indexUrl !in urls) store.write(urls + indexUrl)
    }

    override suspend fun removeRepository(indexUrl: String) = mutex.withLock {
        store.write(store.read().filterNot { it == indexUrl })
        // Drop only the entries this repository supplied; the remaining curated indexes stay listed.
        val kept = origins.filterValues { it != indexUrl }
        origins = kept
        snapshot = snapshot.filter { it.packageName in kept }
    }

    override suspend fun repositories(): List<String> = mutex.withLock {
        store.read().distinct()
    }

    /** All-or-nothing refresh preserves the previous snapshot on network or parsing failure. */
    override suspend fun refresh(): List<ExtensionDescriptor> = mutex.withLock {
        seedLocked()
        val indexed = store.read().distinct().map { url -> url to fetchOrBundled(url) }
        val merged = mergeIndexed(indexed)
        val entries = merged.map { it.second }
        // Ambiguous package ownership is not resolved by silently trusting the highest version.
        require(entries.map { it.packageName }.distinct().size == entries.size) {
            "Conflicting package names across repositories"
        }
        origins = merged.associate { (url, descriptor) -> descriptor.packageName to url }
        entries.toList().also { snapshot = it }
    }

    /**
     * Curated slices are subsets of the universal catalog: a slice never claims a package
     * another registered index already provides. Cross-ownership between non-curated indexes
     * stays untouched so the conflict check below can still reject a hijacking repository.
     */
    private fun mergeIndexed(indexed: List<Pair<String, List<ExtensionDescriptor>>>): List<Pair<String, ExtensionDescriptor>> {
        val claimed = indexed.filterNot { (url, _) -> url in EmbeddedRepositories.CURATED_SLICES }
            .flatMap { (_, descriptors) -> descriptors }.map { it.packageName }.toSet()
        return indexed.flatMap { (url, descriptors) ->
            val owned = if (url in EmbeddedRepositories.CURATED_SLICES) {
                descriptors.filterNot { it.packageName in claimed }
            } else {
                descriptors
            }
            owned.map { url to it }
        }
    }

    /** A curated index that cannot be reached online falls back to its bundled snapshot. */
    private suspend fun fetchOrBundled(url: String): List<ExtensionDescriptor> = try {
        parser.parse(client.fetchPayload(url), url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        embeddedEntries[url] ?: throw error
    }

    override suspend fun available(): List<ExtensionDescriptor> = mutex.withLock {
        seedLocked()
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
