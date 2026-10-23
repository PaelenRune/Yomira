package com.yomira.data.extension

import com.yomira.domain.extension.SourceChapter
import com.yomira.domain.extension.SourceManga
import com.yomira.domain.extension.SourcePage
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.*
import org.junit.Test

class SandboxSourceBridgeTest {
    @Test fun `access is checked before runtime invocation`() = runTest {
        val runtime = Runtime()
        SandboxSourceBridge(runtime).getMangaList("package", "source", "series")
        assertEquals(listOf("verify", "list"), runtime.calls)
    }

    @Test(expected = SecurityException::class)
    fun `untrusted package never reaches runtime`() = runTest {
        val runtime = Runtime(deny = true)
        try {
            SandboxSourceBridge(runtime).getMangaList("package", "source", "series")
        } finally {
            assertEquals(listOf("verify"), runtime.calls)
        }
    }

    @Test(expected = TimeoutCancellationException::class)
    fun `slow runtime is cancelled within deadline`() = runTest {
        SandboxSourceBridge(Runtime(waitMillis = 1000), timeoutMillis = 100)
            .getMangaList("package", "source", "series")
    }

    private class Runtime(val deny: Boolean = false, val waitMillis: Long = 0) : IsolatedExtensionRuntime {
        val calls = mutableListOf<String>()
        override suspend fun verifyAccess(packageName: String, sourceId: String) {
            calls += "verify"
            if (deny) throw SecurityException("Untrusted certificate")
        }
        override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
            calls += "list"
            delay(waitMillis)
            return emptyList()
        }
        override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga) = emptyList<SourceChapter>()
        override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter) = emptyList<SourcePage>()
    }
}
