package com.yomira.feature.details

import com.yomira.core.model.AdaptationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailCopyTest {
    @Test fun `each watchable kind has a distinct default label`() {
        val copy = DetailCopy()

        assertEquals("Anime Uyarlaması", copy.adaptationLabel(AdaptationKind.ANIME))
        assertEquals("Hentai OVA", copy.adaptationLabel(AdaptationKind.HENTAI_OVA))
        assertEquals("Dizi / Canlı Uyarlama", copy.adaptationLabel(AdaptationKind.LIVE_ACTION))
        assertEquals("Donghua Uyarlaması", copy.adaptationLabel(AdaptationKind.DONGHUA))
        assertEquals("Özel Bölüm", copy.adaptationLabel(AdaptationKind.SPECIAL))
        assertEquals("12 Bölüm", String.format(copy.adaptationEpisodes, 12))
    }

    @Test fun `adaptation copy can be localized by the app`() {
        val copy = DetailCopy(
            adaptationAnime = "Anime adaptation",
            adaptationHentaiOva = "Adult OVA",
            adaptationLiveAction = "Live action",
            adaptationDonghua = "Chinese animation",
            adaptationSpecial = "Special",
            adaptationEpisodes = "%1\$d episodes",
        )

        assertEquals(listOf("Anime adaptation", "Adult OVA", "Live action", "Chinese animation", "Special"),
            AdaptationKind.entries.map(copy::adaptationLabel))
        assertEquals("24 episodes", String.format(copy.adaptationEpisodes, 24))
    }
}
