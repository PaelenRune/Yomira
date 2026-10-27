package com.yomira.app.settings

import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CacheManagerTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `size includes every owner and formats bytes`() = runTest {
        val manager = CacheManager(Cache(1_024), Cache(1_024), Cache(1_024), StandardTestDispatcher(testScheduler))

        assertEquals(3_072L, manager.getCacheSizeBytes())
        assertEquals("3.0 KB", manager.getCacheSizeFormatted())
    }

    @Test fun `granular image clear leaves network and temporary files untouched`() = runTest {
        val images = Cache(10)
        val network = Cache(20)
        val temporary = Cache(30)
        val manager = CacheManager(images, network, temporary, StandardTestDispatcher(testScheduler))

        manager.clearImageCache()

        assertEquals(0L, images.bytes)
        assertEquals(20L, network.bytes)
        assertEquals(30L, temporary.bytes)
    }

    @Test fun `granular network clear leaves images and temporary files untouched`() = runTest {
        val images = Cache(10)
        val network = Cache(20)
        val temporary = Cache(30)
        val manager = CacheManager(images, network, temporary, StandardTestDispatcher(testScheduler))

        manager.clearNetworkCache()

        assertEquals(10L, images.bytes)
        assertEquals(0L, network.bytes)
        assertEquals(30L, temporary.bytes)
    }

    @Test fun `all cache clear succeeds and reports actual freed bytes`() = runTest {
        val manager = CacheManager(Cache(10), Cache(20), Cache(30), StandardTestDispatcher(testScheduler))

        assertEquals(60L, manager.clearAndGetFreedBytes())
        assertEquals(0L, manager.getCacheSizeBytes())
        assertTrue(manager.clearAllCache().isSuccess)
    }

    @Test fun `failure is returned but all remaining owners are attempted`() = runTest {
        val error = IOException("image cache unavailable")
        val images = Cache(10, error)
        val network = Cache(20, IOException("network unavailable"))
        val temporary = Cache(30)
        val manager = CacheManager(images, network, temporary, StandardTestDispatcher(testScheduler))

        val result = manager.clearAllCache()

        assertSame(error, result.exceptionOrNull())
        assertEquals(1, error.suppressed.size)
        assertEquals(1, network.clears)
        assertEquals(0L, temporary.bytes)
    }

    @Test fun `cancellation propagates instead of becoming a failed result`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val images = object : ManagedCache {
            override suspend fun sizeBytes() = 0L
            override suspend fun clear() {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val network = Cache(20)
        val manager = CacheManager(images, network, Cache(0), StandardTestDispatcher(testScheduler))
        val operation = async { manager.clearAllCache() }
        entered.await()

        operation.cancel()
        try {
            operation.await()
            throw AssertionError("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertEquals(0, network.clears)
        }
        // Cancellation releases the mutex.
        assertEquals(20L, manager.getCacheSizeBytes())
    }

    @Test fun `concurrent cache operations are serialized`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        val images = object : ManagedCache {
            override suspend fun sizeBytes(): Long { reads++; return 0L }
            override suspend fun clear() { entered.complete(Unit); release.await() }
        }
        val manager = CacheManager(images, Cache(0), Cache(0), StandardTestDispatcher(testScheduler))
        val clear = launch { manager.clearImageCache() }
        entered.await()
        val size = async { manager.getCacheSizeBytes() }
        runCurrent()
        assertEquals(0, reads)

        release.complete(Unit)
        clear.join()
        assertEquals(0L, size.await())
        assertEquals(1, reads)
    }

    @Test fun `owner work runs on supplied IO dispatcher`() = runTest {
        Executors.newSingleThreadExecutor { task -> Thread(task, "cache-test-io") }
            .asCoroutineDispatcher().use { dispatcher ->
                val cache = object : ManagedCache {
                    override suspend fun sizeBytes(): Long {
                        assertEquals("cache-test-io", Thread.currentThread().name)
                        return 0L
                    }
                    override suspend fun clear() {
                        assertEquals("cache-test-io", Thread.currentThread().name)
                    }
                }
                val manager = CacheManager(cache, cache, cache, dispatcher)
                manager.getCacheSizeFormatted()
                assertTrue(manager.clearAllCache().isSuccess)
            }
    }

    @Test fun `temporary cleanup excludes live cache journals and preserves root and user files`() = runTest {
        val root = folder.newFolder("cache").toPath()
        val journal = Files.createDirectories(root.resolve("owners/images")).resolve("journal")
        Files.write(journal, ByteArray(9))
        val download = Files.createDirectories(root.resolve("downloads/extracting")).resolve("temp.dex")
        Files.write(download, ByteArray(42))
        val database = folder.newFile("library.db").toPath()
        Files.write(database, ByteArray(77))
        val temporary = TemporaryDirectoryCache({ listOf(root) }, { listOf(journal.parent) })

        assertEquals(42L, temporary.sizeBytes())
        temporary.clear()

        assertTrue(Files.exists(root))
        assertTrue(Files.exists(journal))
        assertFalse(Files.exists(download.parent))
        assertEquals(77L, Files.size(database))
        assertEquals(0L, temporary.sizeBytes())
        temporary.clear()
    }

    @Test fun `missing and overlapping directories do not double count or fail`() = runTest {
        val root = folder.newFolder("cache").toPath()
        val nested = Files.createDirectories(root.resolve("downloads"))
        Files.write(nested.resolve("page.tmp"), ByteArray(12))
        val temporary = TemporaryDirectoryCache({ listOf(nested, root, root, root.resolve("missing")) })

        assertEquals(12L, temporary.sizeBytes())
        temporary.clear()
        assertEquals(0L, temporary.sizeBytes())
        assertTrue(Files.exists(root))
    }

    private class Cache(var bytes: Long, private val failure: Exception? = null) : ManagedCache {
        var clears = 0
        override suspend fun sizeBytes() = bytes
        override suspend fun clear() {
            clears++
            failure?.let { throw it }
            bytes = 0
        }
    }
}
