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

    @Test fun `index icon field variants resolve to HTTPS artwork`() {
        for (field in listOf("icon", "icon_url", "iconUrl")) {
            val withIcon = entry.replace("\"unknown\":true", "\"$field\":\"icon/example.png\"")
            val result = parser.parse("[$withIcon]", url).single()
            assertEquals("https://example.org/repo/icon/example.png", result.iconUrl)
            assertEquals(url, result.repositoryUrl)
        }
    }

    @Test fun `invalid optional icon keeps entry and repo favicon available`() {
        val withIcon = entry.replace("\"unknown\":true", "\"icon_url\":\"http://unsafe.test/icon.png\"")
        val result = parser.parse("[$withIcon]", url).single()
        assertNull(result.iconUrl)
        assertEquals(listOf("https://example.org/favicon.ico"),
            com.yomira.domain.extension.ExtensionLogo.candidates(result.iconUrl, result.repositoryUrl))
    }

    @Test(expected = OutdatedRepositoryClientException::class)
    fun `identifies upstream outdated app placeholder before parsing legacy fields`() {
        parser.parse("""[{"name":"Outdated App","pkg":"stub"},{"name":"Update to Mihon 0.20.1+","pkg":"stub2"}]""", url)
    }

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
