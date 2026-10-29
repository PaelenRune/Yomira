package com.yomira.data.extension

import com.yomira.domain.extension.OnboardingWizardManager
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepositorySyncTest {
    private val upstreamUrl = "https://example.org/upstream.json"
    private val repository = EmbeddedRepository("https://example.org/index.json", "[]", upstreamUrl)

    @Test fun `normal and encoded indexes parse identically including UTF8 and large IDs`() {
        val payload = EmbeddedRepositories.refreshedPayload(upstream())
        val encoded = encode(payload)
        assertFalse(encoded.contains("Örnek"))
        assertFalse(encoded.contains("source.example.org"))
        val parser = KeiyoushiRepoParser()
        assertEquals(parser.parse(payload, repository.indexUrl), parser.parse(encoded, repository.indexUrl))
        assertEquals("9223372036854775806", parser.parse(encoded, repository.indexUrl).first().sources.single().id)
        assertTrue(parser.parse(encoded, repository.indexUrl).all { it.nsfw })
        assertEquals("https://source.example.org/0.png", parser.parse(encoded, repository.indexUrl).first().iconUrl)
    }

    @Test fun `personal build registers only the universal index by default`() {
        assertEquals(listOf(OnboardingWizardManager.KEIYOUSHI_INDEX), EmbeddedRepositories.DEFAULTS)
        assertEquals(3, EmbeddedRepositories.load().size)
    }

    @Test fun `only explicit refresh fetches user repositories and failure retains snapshot`() = runTest {
        var requests = 0
        var response = EmbeddedRepositories.refreshedPayload(upstream())
        val manager = manager {
            assertEquals(upstreamUrl, it)
            requests++
            response
        }
        assertTrue(manager.available().isEmpty())
        assertEquals(0, requests)
        assertEquals(100, manager.refresh().size)
        manager.available()
        assertEquals(1, requests)
        response = EmbeddedRepositories.refreshedPayload(upstream(version = 2))
        assertTrue(manager.refresh().all { it.versionCode == 2L })
        response = "{}"
        try {
            manager.refresh()
            fail("Expected invalid repository")
        } catch (_: IllegalArgumentException) { }
        assertTrue(manager.available().all { it.versionCode == 2L })
        assertEquals(3, requests)
    }

    @Test fun `user configured repository is fetched once without dropping entries`() = runTest {
        var requests = 0
        val manager = manager { requests++; EmbeddedRepositories.refreshedPayload(upstream()) }
        val entries = manager.refresh()
        assertEquals(1, requests)
        assertEquals(100, entries.size)
        assertEquals(100, entries.map { it.packageName }.distinct().size)
    }

    @Test fun `sync cancellation propagates rather than being hidden as offline`() = runTest {
        try {
            manager { throw CancellationException("cancelled") }.refresh()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypted resource must carry an encoding header`() {
        RepositoryPayload.decode("[]", encrypted = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed encoded payload is rejected`() {
        RepositoryPayload.decode("YOMIRA-XOR-1:!invalid!")
    }

    private fun manager(
        fetch: suspend (String) -> String,
    ) = ExtensionManager(object : RepositoryStore {
        override suspend fun read() = listOf(upstreamUrl)
        override suspend fun write(urls: List<String>) = Unit
    }, RepositoryClient(fetch), KeiyoushiRepoParser(), InstalledExtensionProvider { emptyList() })

    private fun upstream(version: Int = 1): String = """{"extensionList":{"extensions":[${
        (0 until 100).joinToString(",") { index ->
            """{"name":"Örnek $index","packageName":"example.item$index","resources":{"apkUrl":"https://source.example.org/$index.apk","iconUrl":"https://source.example.org/$index.png"},"sources":[{"id":"9223372036854775806","name":"Örnek","language":"en"}],"contentWarning":"CONTENT_WARNING_NSFW","versionName":"$version","versionCode":$version}"""
        }
    }]}}"""

    private fun encode(payload: String): String {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val key = "yomira-repository-v1".toByteArray(Charsets.UTF_8)
        return "YOMIRA-XOR-1:" + Base64.getEncoder().encodeToString(ByteArray(bytes.size) {
            (bytes[it].toInt() xor key[it % key.size].toInt()).toByte()
        })
    }
}
