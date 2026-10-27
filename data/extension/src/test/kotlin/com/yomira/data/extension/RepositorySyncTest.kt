package com.yomira.data.extension

import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepositorySyncTest {
    private val upstreamUrl = "https://example.org/upstream.json"
    private val repository = EmbeddedRepository("https://example.org/global.json", "[]", upstreamUrl, "global")

    @Test fun `normal and encoded indexes parse identically including UTF8 and large IDs`() {
        val payload = EmbeddedRepositories.refreshedPayload(repository, upstream())
        val encoded = encode(payload)
        assertFalse(encoded.contains("Örnek"))
        assertFalse(encoded.contains("source.example.org"))
        val parser = KeiyoushiRepoParser()
        assertEquals(parser.parse(payload, repository.indexUrl), parser.parse(encoded, repository.indexUrl))
        assertEquals("9223372036854775806", parser.parse(encoded, repository.indexUrl).first().sources.single().id)
        assertTrue(parser.parse(encoded, repository.indexUrl).all { it.nsfw })
    }

    @Test fun `bundled resources are encoded and plaintext exports are not on classpath`() {
        val loader = EmbeddedRepositories::class.java
        for (group in listOf("turkish", "western", "global")) {
            val encoded = loader.getResourceAsStream("/yomira/repositories/$group/index.enc")!!
                .bufferedReader().use { it.readText() }
            assertTrue(encoded.startsWith("YOMIRA-XOR-1:"))
            assertFalse(encoded.contains("pkgName"))
            assertTrue(RepositoryPayload.decode(encoded, encrypted = true).startsWith("["))
            assertNull(loader.getResource("/yomira/repositories/$group/index.min.json"))
            assertNull(loader.getResource("/yomira/repositories/export/$group/index.min.json"))
        }
        assertTrue(EmbeddedRepositories.load().flatMap {
            KeiyoushiRepoParser().parse(it.payload, it.indexUrl)
        }.size > 1000)
    }

    @Test fun `automatic sync is throttled explicit refresh sees new versions and offline retains snapshot`() = runTest {
        var requests = 0
        var response = upstream()
        val manager = manager {
            assertEquals(upstreamUrl, it)
            requests++
            response
        }
        assertEquals(100, manager.available().size)
        manager.available()
        assertEquals(1, requests)
        response = upstream(version = 2)
        assertTrue(manager.refresh().all { it.versionCode == 2L })
        response = "{}"
        assertTrue(manager.refresh().all { it.versionCode == 2L })
        assertEquals(3, requests)
    }

    @Test fun `upstream is fetched once for all groups and their ownership does not overlap`() = runTest {
        var requests = 0
        val repositories = listOf("turkish", "western", "global").map { group ->
            repository.copy(indexUrl = "https://example.org/$group.json", group = group, westernPackages = listOf("item0"))
        }
        val manager = manager(repositories) { requests++; upstream() }
        val entries = manager.available()
        assertEquals(1, requests)
        assertEquals(100, entries.size)
        assertEquals(100, entries.map { it.packageName }.distinct().size)
    }

    @Test fun `sync cancellation propagates rather than being hidden as offline`() = runTest {
        try {
            manager { throw CancellationException("cancelled") }.available()
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
        repositories: List<EmbeddedRepository> = listOf(repository),
        fetch: suspend (String) -> String,
    ) = ExtensionManager(object : RepositoryStore {
        override suspend fun read() = emptyList<String>()
        override suspend fun write(urls: List<String>) = Unit
    }, RepositoryClient(fetch), KeiyoushiRepoParser(), InstalledExtensionProvider { emptyList() }, repositories)

    private fun upstream(version: Int = 1): String = """{"extensionList":{"extensions":[${
        (0 until 100).joinToString(",") { index ->
            """{"name":"Örnek $index","packageName":"example.item$index","resources":{"apkUrl":"https://source.example.org/$index.apk"},"sources":[{"id":"9223372036854775806","name":"Örnek","language":"en"}],"contentWarning":"CONTENT_WARNING_NSFW","versionName":"$version","versionCode":$version}"""
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
