package com.yomira.app.catalog

import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-rule tests for the merge-before-insert guard: one series must never appear as
 * two cards just because AniList speaks romaji and MangaDex speaks English.
 */
class CatalogMergerTest {
    private fun seeded() = MangaEntity(
        id = "seed:attack-on-titan",
        title = "Shingeki no Kyojin",
        altTitles = listOf("進撃の巨人"),
        coverUrl = "https://example.org/seed.jpg",
        author = "Hajime Isayama",
        synopsis = "Seeded synopsis",
        score = 8.9,
        status = MangaStatus.COMPLETED,
        genres = listOf("Action"),
        contentRating = ContentRating.SAFE,
        sourceId = "seed",
        readingMode = ReadingMode.MANGA_RTL,
    )

    private fun remote() = Manga(
        id = "anilist:431",
        title = "Attack on Titan",
        altTitles = listOf("Shingeki no Kyojin"),
        coverUrl = "https://example.org/ani.jpg",
        bannerUrl = "https://example.org/banner.jpg",
        synopsis = null,
        score = 9.1,
        status = MangaStatus.COMPLETED,
        genres = listOf("Action", "Drama", "Thriller"),
        contentRating = ContentRating.SAFE,
        sourceId = "anilist",
    )

    @Test fun englishRecordTitleWinsAndRomajiStaysSearchable() {
        val merged = CatalogMerger.mergedEntity(seeded(), remote())
        assertEquals("Attack on Titan", merged.title)
        assertTrueAll(merged.altTitles, "Shingeki no Kyojin", "進撃の巨人")
    }

    @Test fun identityColumnsNeverMoveSoChaptersAndLibrarySurvive() {
        val merged = CatalogMerger.mergedEntity(seeded(), remote())
        assertEquals("seed:attack-on-titan", merged.id)
        assertEquals("seed", merged.sourceId)
        assertEquals(ReadingMode.MANGA_RTL, merged.readingMode)
    }

    @Test fun volatileFactsRefreshWhileMissingTextFallsBackToStoredValue() {
        val merged = CatalogMerger.mergedEntity(seeded(), remote())
        assertEquals("https://example.org/ani.jpg", merged.coverUrl)
        assertEquals("https://example.org/banner.jpg", merged.bannerUrl)
        assertEquals(9.1, merged.score!!, 0.0001)
        // The remote record had no synopsis/author: the seeded values must survive.
        assertEquals("Seeded synopsis", merged.synopsis)
        assertEquals("Hajime Isayama", merged.author)
        // Genres union without duplicates.
        assertEquals(listOf("Action", "Drama", "Thriller"), merged.genres)
    }

    @Test fun mergeNeverDemotesAdultTitlesIntoThePublicCatalog() {
        val explicit = seeded().copy(contentRating = ContentRating.EXPLICIT)
        assertEquals(ContentRating.EXPLICIT, CatalogMerger.mergedEntity(explicit, remote()).contentRating)
        val matureRemote = remote().copy(contentRating = ContentRating.MATURE)
        assertEquals(ContentRating.MATURE, CatalogMerger.mergedEntity(seeded(), matureRemote).contentRating)
    }

    @Test fun unknownRemoteStatusKeepsTheStoredOne() {
        val merged = CatalogMerger.mergedEntity(seeded(), remote().copy(status = MangaStatus.UNKNOWN))
        assertEquals(MangaStatus.COMPLETED, merged.status)
    }

    @Test fun identityCandidatesStartWithTheDisplayTitle() {
        assertEquals(listOf("Attack on Titan", "Shingeki no Kyojin"), remote().identityCandidates())
    }

    private fun assertTrueAll(values: List<String>, vararg expected: String) {
        expected.forEach { org.junit.Assert.assertTrue("$it missing from $values", it in values) }
    }
}
