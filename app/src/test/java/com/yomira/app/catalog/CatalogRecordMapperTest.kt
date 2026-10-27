package com.yomira.app.catalog

import com.yomira.core.model.ContentRating
import com.yomira.core.model.ExternalRating
import com.yomira.core.model.HomeShelf
import com.yomira.core.network.RemoteMetadata
import com.yomira.domain.classifier.ContentRatingClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRecordMapperTest {
    private val classifier = ContentRatingClassifier()

    @Test fun classifierCorrectionOverridesProviderPornographicLabel() {
        val record = RemoteMetadata("test", "1", "I Formed a MILF Party with the Heroes' Mothers",
            contentRating = ExternalRating.PORNOGRAPHIC)
        assertEquals(ContentRating.MATURE, CatalogRecordMapper.toMangaOrNull(record, classifier)?.contentRating)
    }

    @Test fun genuinelyExplicitRecordStillCannotEnterPublicFeed() {
        val record = RemoteMetadata("test", "2", "Uncorrected title", contentRating = ExternalRating.PORNOGRAPHIC)
        assertNull(CatalogRecordMapper.toMangaOrNull(record, classifier))
    }

    @Test fun formatEvidenceSurvivesGenreOnlyPersistence() {
        val record = RemoteMetadata("test", "3", "An adult series", genres = listOf("Hentai"),
            tags = listOf("Long Strip"), publicationType = "Manhwa", contentRating = ExternalRating.PORNOGRAPHIC)
        val manga = requireNotNull(CatalogRecordMapper.toMangaOrNull(record, classifier, allowExplicit = true))
        assertEquals("Manhwa", manga.publicationType)
        assertTrue(manga.genres.containsAll(listOf("Hentai", "Long Strip", "Manhwa")))
        assertTrue(HomeShelf.WEBTOON.matches(manga.copy(publicationType = null)))
    }
}
