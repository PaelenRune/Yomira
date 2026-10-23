package com.yomira.data.extension

import com.yomira.domain.extension.SourceBridge
import com.yomira.domain.extension.SourceChapter
import com.yomira.domain.extension.SourceManga
import com.yomira.domain.extension.SourcePage
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/**
 * IPC boundary, NOT an in-process DexClassLoader. The host must implement this using a separate
 * sandbox process and a compatible Mihon API runtime. Untrusted extension code must never run in
 * the app process. Calls must be cancellable and return DTOs only, not plugin-defined objects.
 */
interface IsolatedExtensionRuntime : SourceBridge {
    /** Rechecks package certificate trust and verifies source ownership on every invocation. */
    suspend fun verifyAccess(packageName: String, sourceId: String)
}

class SandboxSourceBridge(
    private val runtime: IsolatedExtensionRuntime,
    private val timeoutMillis: Long = 30_000,
    maxConcurrentCalls: Int = 4,
) : SourceBridge {
    init {
        require(timeoutMillis > 0)
        require(maxConcurrentCalls > 0)
    }

    private val permits = Semaphore(maxConcurrentCalls)

    override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
        require(query.isNotBlank() && page > 0)
        return invoke(packageName, sourceId) { runtime.getMangaList(packageName, sourceId, query, page).toList() }
    }

    override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga): List<SourceChapter> =
        invoke(packageName, sourceId) { runtime.getChapterList(packageName, sourceId, manga).toList() }

    override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter): List<SourcePage> =
        invoke(packageName, sourceId) {
            runtime.getPageList(packageName, sourceId, chapter).also { pages ->
                require(pages.all { it.index >= 0 } && pages.map { it.index }.distinct().size == pages.size)
                pages.forEach { page ->
                    val uri = java.net.URI(page.imageUrl)
                    require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
                }
            }.sortedBy { it.index }
        }

    private suspend fun <T> invoke(packageName: String, sourceId: String, block: suspend () -> T): T {
        require(packageName.isNotBlank() && sourceId.isNotBlank())
        return withTimeout(timeoutMillis) {
            permits.withPermit {
                runtime.verifyAccess(packageName, sourceId)
                block()
            }
        }
    }
}
