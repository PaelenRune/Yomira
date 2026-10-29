package com.yomira.domain.extension

import org.junit.Assert.*
import org.junit.Test

class ExtensionLogoTest {
    private val repo = "https://repo.example.org/catalog/index.json"

    @Test fun relativeIndexIconResolvesBesideIndex() {
        assertEquals("https://repo.example.org/catalog/icon/site.png", ExtensionLogo.resolveIcon("icon/site.png", repo))
    }
    @Test fun absoluteIconWinsBeforeRepositoryFavicon() {
        assertEquals(listOf("https://cdn.example.org/source.png", "https://repo.example.org/favicon.ico"),
            ExtensionLogo.candidates("https://cdn.example.org/source.png", repo))
    }
    @Test fun missingIconFallsBackToRepositoryOriginNotIndexFolder() {
        assertEquals(listOf("https://repo.example.org/favicon.ico"), ExtensionLogo.candidates(null, repo))
    }
    @Test fun noUrlsMeansInitialsOnly() {
        assertTrue(ExtensionLogo.candidates(null, null).isEmpty())
        assertEquals("MT", ExtensionLogo.initials("Manga TR"))
        assertEquals("ÖK", ExtensionLogo.initials("  Örnek — Kaynak  "))
        assertEquals("?", ExtensionLogo.initials("  "))
    }
    @Test fun insecureOrMalformedOptionalIconsCannotBreakRepository() {
        for (bad in listOf("http://cdn.test/icon.png", "file:///icon.png", "https://user:pass@cdn.test/icon.png", "https://[bad")) {
            assertEquals(listOf("https://repo.example.org/favicon.ico"), ExtensionLogo.candidates(bad, repo))
        }
        assertTrue(ExtensionLogo.candidates("icon.png", "not a url").isEmpty())
    }
    @Test fun duplicateIconAndFaviconAreTriedOnlyOnce() {
        assertEquals(1, ExtensionLogo.candidates("/favicon.ico", repo).size)
    }
    @Test fun cacheKeyIsStableCanonicalAndNamespaced() {
        val first = ExtensionLogo.cacheKey("https://CDN.example.org:443/icon.png#ignored")
        assertEquals(first, ExtensionLogo.cacheKey("https://cdn.example.org/icon.png"))
        assertTrue(first.matches(Regex("extension-logo-v1:[0-9a-f]{64}")))
        assertNotEquals(first, ExtensionLogo.cacheKey("https://cdn.example.org/other.png"))
        assertNotEquals(first, ExtensionLogo.cacheKey("https://cdn.example.org/icon.png?v=2"))
    }
    @Test fun cacheAndResolutionPreserveEscapedCdnPathAndQuery() {
        val url = "https://cdn.example.org/icon%2Fsource.png?token=a%2Fb"
        assertEquals(url, ExtensionLogo.resolveIcon(url, repo))
        assertNotEquals(ExtensionLogo.cacheKey(url), ExtensionLogo.cacheKey("https://cdn.example.org/icon/source.png?token=a/b"))
    }
    @Test fun faviconKeepsCustomPortButDropsCredentialsAndQuery() {
        assertEquals("https://repo.test:8443/favicon.ico", ExtensionLogo.favicon("https://repo.test:8443/index.json?key=secret"))
        assertNull(ExtensionLogo.favicon("https://user:pass@repo.test/index.json"))
    }
}
