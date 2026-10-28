package com.yomira.data.extension

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest

class ProtoRepositoryParserTest {
    private val url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"

    @Test fun `captured upstream index parses`() {
        val bytes = javaClass.getResourceAsStream("/keiyoushi-index.pb")!!.use { it.readBytes() }
        val entries = KeiyoushiRepoParser().parse(RepositoryResponse(bytes), url)
        println("Captured index: ${entries.size} extensions, ${entries.sumOf { it.sources.size }} sources")
        assertTrue(entries.size >= 100)
        val mangaFire = entries.single { it.packageName == "eu.kanade.tachiyomi.extension.all.mangafire" }
        assertEquals("6084907896154116083", mangaFire.sources.first { it.language == "en" }.id)
        assertTrue(mangaFire.nsfw) // MIXED content is conservatively treated as adult.
        assertTrue(entries.any { it.nsfw })
        assertTrue(entries.any { !it.nsfw })
        assertTrue(entries.flatMap { it.sources }.all { it.id.toLongOrNull() != null })
    }

    @Test fun `wire parser preserves signed int64 and skips unknown fields`() {
        fun varint(value: Long): ByteArray {
            var v = value
            val result = ArrayList<Byte>()
            do { val b = (v and 127).toInt(); v = v ushr 7; result.add((b or if (v != 0L) 128 else 0).toByte()) } while (v != 0L)
            return result.toByteArray()
        }
        fun number(field: Int, value: Long) = varint((field * 8).toLong()) + varint(value)
        fun bytes(field: Int, value: ByteArray) = varint((field * 8L) + 2) + varint(value.size.toLong()) + value
        fun text(field: Int, value: String) = bytes(field, value.toByteArray())
        val source = number(1, Long.MIN_VALUE) + text(2, "Example") + text(3, "en")
        val resources = text(1, "https://example.org/source.apk") + text(501, "ignored")
        val entry = text(1, "Example") + text(2, "example.package") + bytes(3, resources) + number(5, 42) + text(6, "1.4.1") + number(7, 3) + bytes(8, source)
        val index = text(1, "Test") + bytes(101, bytes(1, entry)) + number(200, 7)
        val item = KeiyoushiRepoParser().parse(RepositoryResponse(index), url).single()
        assertEquals(Long.MIN_VALUE.toString(), item.sources.single().id)
        assertTrue(item.nsfw)
        assertEquals(42L, item.versionCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated protobuf`() {
        KeiyoushiRepoParser().parse(RepositoryResponse(byteArrayOf(10, 20, 1)), url)
    }

    @Test fun `refresh passes binary response through manager`() = runTest {
        val bytes = javaClass.getResourceAsStream("/keiyoushi-index.pb")!!.use { it.readBytes() }
        val client = object : RepositoryClient {
            override suspend fun fetch(indexUrl: String): String = error("Binary must not be decoded as text")
            override suspend fun fetchPayload(indexUrl: String) = RepositoryResponse(bytes, "application/octet-stream")
        }
        val store = object : RepositoryStore {
            override suspend fun read() = listOf(url)
            override suspend fun write(urls: List<String>) = Unit
        }
        val manager = ExtensionManager(store, client, KeiyoushiRepoParser(), InstalledExtensionProvider { emptyList() })
        assertTrue(manager.refresh().size >= 100)
        assertEquals(url, manager.repositoryUrl("eu.kanade.tachiyomi.extension.all.mangafire"))
    }
}
