package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicShelvesTest {

    private fun manga(
        id: String,
        score: Double? = null,
        genres: List<String> = emptyList(),
        popularity: Long? = null,
    ) = Manga(
        id = id,
        title = id,
        score = score,
        genres = genres,
        popularity = popularity,
        contentRating = ContentRating.SAFE,
        sourceId = "test",
    )

    @Test fun `rotation noise is deterministic for a key and cycle`() {
        val first = rotationNoise("series-a", 12)
        assertEquals(first, rotationNoise("series-a", 12), 0.0)
        assertTrue(first in 0.0..1.0)
        assertTrue(first != rotationNoise("series-a", 13))
    }

    @Test fun `popular shelf keeps the highest score at the top`() {
        val catalog = listOf(
            manga("low", 4.0),
            manga("great", 9.5),
            manga("mid", 7.0),
        )
        val shelf = popularTrendingShelf(catalog, updated = emptyList(), seed = 3, limit = 3)
        assertEquals("great", shelf.first().id)
        assertEquals(3, shelf.size)
    }

    @Test fun `global reader counts beat niche perfect scores and recent updates`() {
        val hits = listOf("Solo Leveling", "One Piece", "Berserk", "Attack on Titan", "Jujutsu Kaisen")
            .map { manga(it, 8.5, popularity = 1_000_000) }
        val niche = manga("niche", 10.0, popularity = 10)
        val catalog = hits + niche
        val ranked = popularTrendingShelf(catalog, listOf(niche), seed = 3)
        assertEquals(hits.map { it.id }.toSet(), ranked.take(5).map { it.id }.toSet())
        assertEquals(ranked, popularTrendingShelf(catalog.reversed(), listOf(niche), seed = 999))
    }

    @Test fun `popularity uses log normalization and specified consensus weights`() {
        val famous = manga("famous", 3.0, popularity = 1_000_000)
        val contender = manga("contender", 9.0, popularity = 10_000)
        // Log scale allows a well-rated established contender to beat a poorly rated giant.
        assertEquals("contender", popularTrendingShelf(listOf(famous, contender), emptyList(), 0).first().id)
    }

    @Test fun `missing and zero popularity stay finite with stable ties and neutral score`() {
        val catalog = listOf(manga("b", 7.0, popularity = 0), manga("a"))
        assertEquals(listOf("a", "b"), popularTrendingShelf(catalog, emptyList(), 1).map { it.id })
        assertTrue(popularTrendingShelf(emptyList(), emptyList(), 0).isEmpty())
    }

    @Test fun `personal shelf follows taste genres and skips read series`() {
        val catalog = listOf(
            manga("read-fantasy", 8.0, listOf("Fantasy")),
            manga("fantasy-match", 7.0, listOf("Fantasy", "Adventure")),
            manga("romance", 8.0, listOf("Romance")),
        )
        val shelf = personalizedShelf(
            all = catalog,
            readIds = setOf("read-fantasy"),
            savedIds = setOf("read-fantasy"),
            seed = 1,
            limit = 2,
        )
        assertEquals("fantasy-match", shelf.first().id)
        assertFalse(shelf.any { it.id == "read-fantasy" })
    }

    @Test fun `isekai shelf only carries isekai or fantasy series`() {
        val catalog = listOf(
            manga("isekai", 7.0, listOf("Isekai")),
            manga("fantasy", 7.0, listOf("Fantasy")),
            manga("romance", 9.5, listOf("Romance")),
        )
        val shelf = isekaiFantasyShelf(catalog, seed = 5)
        assertEquals(listOf("fantasy", "isekai"), shelf.map { it.id }.sorted())
    }

    @Test fun `editors shelf prefers elite scores`() {
        val catalog = listOf(
            manga("elite", 9.1),
            manga("good", 8.4),
            manga("average", 6.0),
            manga("unscored", null),
        )
        val shelf = editorsChoiceShelf(catalog, seed = 7, limit = 2)
        assertEquals(2, shelf.size)
        assertTrue(shelf.all { (it.score ?: 0.0) >= 8.0 })
    }
}
