package com.yomira.desktop.model

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.network.RemoteMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mapping and copy rules shared by the cards, dossier and reader. */
class DesktopModelsTest {

    @Test
    fun `english display title wins and the card keeps provider identity`() {
        val record = RemoteMetadata(
            sourceId = "mangadex",
            remoteId = "abc",
            title = "Shingeki no Kyojin",
            englishTitle = "Attack on Titan",
            coverUrl = "https://uploads.example/cover.jpg",
            bannerUrl = "http://insecure.example/banner.jpg",
            year = 2009,
            status = MangaStatus.COMPLETED,
        )
        val item = record.toCatalogItem()
        assertEquals("Attack on Titan", item.title)
        assertEquals("mangadex:abc", item.id)
        assertEquals("abc", item.remoteId)
        assertEquals("https://uploads.example/cover.jpg", item.coverUrl)
        assertNull("non-HTTPS artwork must be dropped", item.bannerUrl)
    }

    @Test
    fun `provider html is flattened before it reaches compose text`() {
        val record = RemoteMetadata(
            sourceId = "bakaupdates",
            remoteId = "7",
            title = "One Piece",
            description = "<p>Korsanlar çağı.</p><br/><b>Luffy</b> &amp; tayfası.",
        )
        assertEquals("Korsanlar çağı. Luffy & tayfası.", record.toCatalogItem().synopsis)
        assertNull(plainText("   "))
        assertNull(plainText(null))
        assertEquals("plain text", plainText("plain text"))
    }

    @Test
    fun `title identity ignores case width and punctuation`() {
        assertEquals(normalizeTitle("ＡＴＴＡＣＫ on Titan!!"), normalizeTitle("attack on titan"))
        assertTrue(normalizeTitle("Solo Leveling: Ragnarok").startsWith("solo leveling"))
    }

    @Test
    fun `manga detail keeps the classifier verdict and readable source`() {
        val manga = Manga(
            id = "mangadex:xyz",
            title = "Solo Leveling",
            genres = listOf("Action", "Action", "Fantasy"),
            contentRating = ContentRating.MATURE,
            sourceId = "mangadex",
        )
        val item = manga.toCatalogItem()
        assertEquals(ContentRating.MATURE, item.contentRating)
        assertEquals(listOf("Action", "Fantasy"), item.genres)
        assertEquals("xyz", item.remoteId)
        assertTrue(item.isReadableOnDesktop)
        assertFalse(item.copy(sourceId = "anilist").isReadableOnDesktop)
    }

    @Test
    fun `chapter labels keep half chapters and never invent a title`() {
        val chapter = DesktopChapter(id = "c1", mangaId = "m1", number = 12.5)
        assertEquals("Bölüm 12.5", chapter.displayLabel)
        assertEquals("Bölüm 7", chapter.copy(number = 7.0).displayLabel)
        assertTrue(chapter.isReadable)
        assertFalse(chapter.copy(externalUrl = "https://scan.example/c1").isReadable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reader pages must be https`() {
        DesktopPage(index = 0, url = "http://insecure.example/page.png")
    }
}
