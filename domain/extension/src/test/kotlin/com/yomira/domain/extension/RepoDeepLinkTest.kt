package com.yomira.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Test

class RepoDeepLinkTest {
    private val index = OnboardingWizardManager.KEIYOUSHI_INDEX

    @Test fun `all supported schemes with url param are accepted`() {
        listOf("yomira://add-repo", "yomira://repo", "tachiyomi://add-repo").forEach { base ->
            assertEquals(RepoDeepLinkResult.Valid(index), RepoDeepLink.parse("$base?url=$index"))
        }
    }

    @Test fun `index param and percent encoding are supported`() {
        val encoded = java.net.URLEncoder.encode(index, "UTF-8")
        assertEquals(RepoDeepLinkResult.Valid(index), RepoDeepLink.parse("yomira://add-repo?index=$encoded"))
    }

    @Test fun `non https or missing url is invalid`() {
        assertEquals(RepoDeepLinkResult.Invalid, RepoDeepLink.parse("yomira://add-repo?url=http://example.org/index.min.json"))
        assertEquals(RepoDeepLinkResult.Invalid, RepoDeepLink.parse("yomira://add-repo?url=javascript:alert(1)"))
        assertEquals(RepoDeepLinkResult.Invalid, RepoDeepLink.parse("yomira://add-repo"))
        assertEquals(RepoDeepLinkResult.Invalid, RepoDeepLink.parse("tachiyomi://add-repo?url=https://user:pw@example.org/i.json"))
        assertEquals(RepoDeepLinkResult.Invalid, RepoDeepLink.parse("yomira://add-repo?url=https://example.org/%broken"))
    }

    @Test fun `unrelated links are ignored`() {
        assertEquals(RepoDeepLinkResult.NotRepoLink, RepoDeepLink.parse(null))
        assertEquals(RepoDeepLinkResult.NotRepoLink, RepoDeepLink.parse("https://example.org/?url=$index"))
        assertEquals(RepoDeepLinkResult.NotRepoLink, RepoDeepLink.parse("yomira://settings?url=$index"))
    }
}
