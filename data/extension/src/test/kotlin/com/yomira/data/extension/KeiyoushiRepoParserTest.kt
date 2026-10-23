package com.yomira.data.extension

import org.junit.Assert.*
import org.junit.Test

class KeiyoushiRepoParserTest {
    private val parser = KeiyoushiRepoParser()
    private val url = "https://example.org/repo/index.min.json"
    private val entry = """{"name":"Example","pkgName":"eu.kanade.tachiyomi.extension.tr.example","apk":"example.apk","lang":"tr","nsfw":0,"version":"1.4.2","code":12,"sources":[{"id":9223372036854775807,"name":"Example","lang":"tr"}],"unknown":true}"""

    @Test fun `parses Keiyoushi index and preserves long source IDs`() {
        val result = parser.parse("[$entry]", url).single()
        assertEquals("https://example.org/repo/apk/example.apk", result.apkUrl)
        assertEquals("9223372036854775807", result.sources.single().id)
        assertEquals(12L, result.versionCode)
        assertFalse(result.nsfw)
    }

    @Test fun `supports absolute apkUrl and boolean nsfw`() {
        val result = parser.parse("[${entry.replace("\"apk\":\"example.apk\"", "\"apkUrl\":\"https://cdn.example.org/example.apk\"").replace("\"nsfw\":0", "\"nsfw\":true")}]", url).single()
        assertTrue(result.nsfw)
        assertEquals("https://cdn.example.org/example.apk", result.apkUrl)
    }

    @Test fun `empty repository is valid`() { assertTrue(parser.parse("[]", url).isEmpty()) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed root`() { parser.parse("{}", url) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects traversal`() { parser.parse("[${entry.replace("example.apk", "../evil.apk")}]", url) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects insecure repository`() { parser.parse("[$entry]", "http://example.org/index.json") }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate packages`() { parser.parse("[$entry,$entry]", url) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects missing adult classification`() { parser.parse("[${entry.replace("\"nsfw\":0,", "")}]", url) }
}
