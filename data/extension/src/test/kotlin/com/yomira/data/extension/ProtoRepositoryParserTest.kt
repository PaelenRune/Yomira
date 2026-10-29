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

    @Test fun `MangaFire raw field seven is extension MIXED and sources have no warning`() {
        val bytes = javaClass.getResourceAsStream("/keiyoushi-index.pb")!!.use { it.readBytes() }
        val index = rawFields(ProtoRepositoryParser.decompress(bytes))
        val extensions = rawFields(index.single { it.first == 101 }.second as ByteArray)
        val fields = extensions.map { rawFields(it.second as ByteArray) }.single { fields ->
            fields.any { it.first == 2 && (it.second as ByteArray).toString(Charsets.UTF_8) == "eu.kanade.tachiyomi.extension.all.mangafire" }
        }
        assertEquals(2L, fields.single { it.first == 7 }.second) // ContentWarning.CONTENT_WARNING_MIXED
        val sources = fields.filter { it.first == 8 }.map { rawFields(it.second as ByteArray) }
        assertEquals(7, sources.size)
        assertTrue(sources.all { source -> source.none { it.first == 7 } })
        assertTrue(sources.any { source -> source.any { it.first == 1 && it.second == 6084907896154116083L } })
        assertTrue(KeiyoushiRepoParser().parse(RepositoryResponse(bytes), url)
            .single { it.packageName == "eu.kanade.tachiyomi.extension.all.mangafire" }.nsfw)
    }

    // Independent fixture inspection: do not rely on the application's field-number mapping.
    private fun rawFields(bytes: ByteArray): List<Pair<Int, Any>> {
        val input = java.io.ByteArrayInputStream(bytes)
        fun varint(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                val byte = input.read()
                require(byte >= 0)
                value = value or ((byte and 127).toLong() shl shift)
                if (byte and 128 == 0) return value
            }
            error("Invalid varint")
        }
        return buildList {
            while (input.available() > 0) {
                val tag = varint().toInt()
                val value: Any = when (tag and 7) {
                    0 -> varint()
                    2 -> ByteArray(varint().toInt()).also { require(input.read(it) == it.size) }
                    else -> error("Unexpected fixture wire type")
                }
                add((tag ushr 3) to value)
            }
        }
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
        val resources = text(1, "https://example.org/source.apk") + text(2, "icon/example.png") + text(501, "ignored")
        val entry = text(1, "Example") + text(2, "example.package") + bytes(3, resources) + number(5, 42) + text(6, "1.4.1") + number(7, 3) + bytes(8, source)
        val index = text(1, "Test") + bytes(101, bytes(1, entry)) + number(200, 7)
        val item = KeiyoushiRepoParser().parse(RepositoryResponse(index), url).single()
        assertEquals(Long.MIN_VALUE.toString(), item.sources.single().id)
        assertTrue(item.nsfw)
        assertEquals(42L, item.versionCode)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/icon/example.png", item.iconUrl)
        assertEquals(url, item.repositoryUrl)
        for (warning in 0L..3L) {
            val warningEntry = text(1, "Example") + text(2, "example.package") + bytes(3, resources) +
                number(5, 42) + number(7, warning) + bytes(8, source)
            val descriptor = KeiyoushiRepoParser().parse(RepositoryResponse(bytes(101, bytes(1, warningEntry))), url).single()
            assertEquals("ContentWarning=$warning", warning == 2L || warning == 3L, descriptor.nsfw)
        }
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
