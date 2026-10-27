package com.yomira.feature.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultCopyTest {
    @Test fun `rail titles default to English for every style`() {
        val copy = VaultCopy()

        assertEquals(
            listOf("🔥 Trending Adult Webtoons", "🌸 Hentai Manga & Classics", "⚡ New Doujinshi", "🌐 Western & 3D Erotic Comics"),
            vaultGenreChips.map(copy::railTitle),
        )
        assertEquals("Popular in Vault", copy.railTitle("unknown"))
    }

    @Test fun `rail titles can be localized by the app`() {
        val copy = VaultCopy(
            railTrendingWebtoons = "🔥 Trend Yetişkin Webtoon'lar",
            railHentaiManga = "🌸 Hentai Manga & Klasikler",
            railNewDoujinshi = "⚡ Yeni Doujinshi'ler",
            railWestern3D = "🌐 Batı & 3D Erotik Çizgi Romanlar",
            railPopular = "Kasa Popülerleri",
        )

        assertEquals(
            listOf("🔥 Trend Yetişkin Webtoon'lar", "🌸 Hentai Manga & Klasikler", "⚡ Yeni Doujinshi'ler", "🌐 Batı & 3D Erotik Çizgi Romanlar"),
            vaultGenreChips.map(copy::railTitle),
        )
        assertEquals("Kasa Popülerleri", copy.railTitle("unknown"))
    }
}
