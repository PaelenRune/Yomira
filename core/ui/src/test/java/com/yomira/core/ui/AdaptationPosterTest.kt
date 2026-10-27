package com.yomira.core.ui

import com.yomira.core.model.AdaptationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptationPosterTest {
    @Test fun `poster labels distinguish watchable formats`() {
        assertEquals("📺 Anime", adaptationPosterLabel(AdaptationKind.ANIME))
        assertEquals("🔞 Hentai OVA", adaptationPosterLabel(AdaptationKind.HENTAI_OVA))
        assertEquals("🐉 Donghua", adaptationPosterLabel(AdaptationKind.DONGHUA))
        assertTrue(adaptationPosterLabel(AdaptationKind.LIVE_ACTION).contains("Live Action"))
        assertEquals("🎬 K-Drama", adaptationPosterLabel(AdaptationKind.LIVE_ACTION, "K_DRAMA"))
    }
}
