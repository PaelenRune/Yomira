package com.yomira.app.settings

import com.yomira.feature.settings.formatCacheSize
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Cache owners must evict through their own APIs, never by removing their live journals. */
interface ManagedCache {
    suspend fun sizeBytes(): Long
    suspend fun clear()
}

/**
 * Serializes settings cache operations off the UI thread. Owners can repopulate their caches
 * while requests are in flight, so sizes are snapshots rather than a promise of zero bytes.
 */
class CacheManager(
    private val images: ManagedCache,
    private val network: ManagedCache,
    private val temporary: ManagedCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    suspend fun getCacheSizeBytes(): Long = onIo { totalSize() }

    suspend fun getCacheSizeFormatted(): String = formatCacheSize(getCacheSizeBytes())

    suspend fun clearImageCache() = onIo { images.clear() }

    suspend fun clearNetworkCache() = onIo { network.clear() }

    suspend fun clearTemporaryCache() = onIo { temporary.clear() }

    suspend fun clearAllCache(): Result<Unit> = onIo {
        try {
            clearOwners()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /** Compatibility with the existing settings feedback, measured under the same lock. */
    suspend fun clearAndGetFreedBytes(): Long = onIo {
        val before = totalSize()
        clearOwners()
        (before - totalSize()).coerceAtLeast(0L)
    }

    private suspend fun totalSize(): Long = images.sizeBytes() + network.sizeBytes() + temporary.sizeBytes()

    private suspend fun clearOwners() {
        var failure: Exception? = null
        for (cache in listOf(images, network, temporary)) {
            currentCoroutineContext().ensureActive()
            try {
                cache.clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A failed disk eviction must not prevent the other caches from being cleared.
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private suspend fun <T> onIo(block: suspend () -> T): T = withContext(ioDispatcher) {
        mutex.withLock { block() }
    }
}

/**
 * Only for expendable directories: never pass filesDir, databases or installed extension
 * directories. Symlinks are not followed. Cache roots and excluded owner directories survive.
 * The provider is evaluated on IO so even lazy cache initialization stays off the main thread.
 */
class TemporaryDirectoryCache(
    private val directories: () -> List<Path>,
    private val excludedDirectories: () -> List<Path> = { emptyList() },
) : ManagedCache {
    override suspend fun sizeBytes(): Long = visit(delete = false)

    override suspend fun clear() { visit(delete = true) }

    private suspend fun visit(delete: Boolean): Long {
        val context = currentCoroutineContext()
        val excluded = excludedDirectories().map { it.toAbsolutePath().normalize() }
        val roots = directories().map { it.toAbsolutePath().normalize() }.distinct()
            .sortedBy { it.nameCount }
            .fold(emptyList<Path>()) { selected, path ->
                if (selected.any { path.startsWith(it) }) selected else selected + listOf(path)
            }
        var size = 0L
        for (root in roots) {
            context.ensureActive()
            if (!Files.exists(root, NOFOLLOW_LINKS)) continue
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    return if (excluded.any { dir.startsWith(it) }) FileVisitResult.SKIP_SUBTREE
                    else FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    if (excluded.any { file.startsWith(it) }) return FileVisitResult.CONTINUE
                    if (attrs.isRegularFile) size += attrs.size()
                    if (delete) Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    context.ensureActive()
                    // Concurrent downloads/evictions may already have removed an entry.
                    if (exc !is NoSuchFileException) throw exc
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    context.ensureActive()
                    if (exc != null && exc !is NoSuchFileException) throw exc
                    // Preserve parents containing an excluded cache owner, too.
                    if (delete && dir != root && excluded.none { it.startsWith(dir) }) {
                        Files.deleteIfExists(dir)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return size
    }
}
