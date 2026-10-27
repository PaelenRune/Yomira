package com.yomira.core.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShelfTest {
    private fun manga(vararg genres: String, status: MangaStatus = MangaStatus.ONGOING) =
        Manga(id = "test", title = "Test", genres = genres.toList(), status = status, sourceId = "test")

    @Test fun `catalog contains two fixed and thirteen distinct dynamic shelves`() {
        assertEquals(15, HomeShelf.entries.size)
        assertEquals(15, HomeShelf.entries.map { it.title }.distinct().size)
        assertEquals(13, HomeShelf.dynamicPool.size)
        assertFalse(HomeShelf.dynamicPool.any { it in HomeShelf.fixed })
    }

    @Test fun `every launch selects two fixed followed by exactly five unique dynamic shelves`() {
        repeat(500) { seed ->
            val selected = HomeShelf.select(Random(seed))
            assertEquals(HomeShelf.fixed, selected.take(2))
            assertEquals(7, selected.size)
            assertEquals(7, selected.distinct().size)
            assertTrue(selected.drop(2).all { it in HomeShelf.dynamicPool })
        }
    }

    @Test fun `seeded sampling is reproducible and reaches every dynamic shelf`() {
        assertEquals(HomeShelf.select(Random(17)), HomeShelf.select(Random(17)))
        val selections = (0..100).map { HomeShelf.select(Random(it)) }
        assertTrue(selections.distinct().size > 1)
        assertEquals(HomeShelf.dynamicPool.toSet(), selections.flatMap { it.drop(2) }.toSet())
    }

    @Test fun `genre and format aliases match only their intended categories`() {
        val examples = mapOf(
            HomeShelf.WESTERN to manga("Western Comic"),
            HomeShelf.ACTION to manga("Shounen"),
            HomeShelf.ROMANCE to manga("Romance"),
            HomeShelf.MYSTERY to manga("Psychological"),
            HomeShelf.ISEKAI to manga("Reincarnation"),
            HomeShelf.HORROR to manga("Dark Fantasy"),
            HomeShelf.SCI_FI to manga("Sci-Fi"),
            HomeShelf.MARTIAL_ARTS to manga("Murim"),
            HomeShelf.WEBTOON to manga("Long Strip"),
            HomeShelf.SEINEN to manga("Seinen"),
            HomeShelf.COMEDY to manga("Slice of Life"),
            HomeShelf.COMPLETED to manga(status = MangaStatus.COMPLETED),
        )
        examples.forEach { (shelf, record) -> assertTrue(shelf.name, shelf.matches(record)) }
        assertFalse(HomeShelf.ISEKAI.matches(manga("Fantasy")))
        assertFalse(HomeShelf.HORROR.matches(manga("Fantasy")))
        assertTrue(HomeShelf.WEBTOON.matches(manga("Manhwa")))
        assertFalse(HomeShelf.COMPLETED.matches(manga("Action")))
        assertTrue(HomeShelf.COMPLETED.matches(manga("Award-Winning")))
    }

    @Test fun `webtoon shelf checks publication format and delimited tags without title guessing`() {
        listOf("Webtoon", "Manhwa", "Pornhwa", "Long-Strip").forEach { format ->
            assertTrue(format, HomeShelf.WEBTOON.matches(manga().copy(publicationType = format)))
            assertTrue(format, HomeShelf.WEBTOON.matches(manga("Drama | $format")))
        }
        listOf("Manga", "Comic", "Manhua", "Notwebtoon").forEach { format ->
            assertFalse(format, HomeShelf.WEBTOON.matches(manga().copy(publicationType = format)))
        }
        assertFalse(HomeShelf.WEBTOON.matches(manga().copy(title = "Webtoon Romance")))
    }

    @Test fun `genre shelves require genuine genre evidence across media`() {
        val western = manga("Western Comic").copy(publicationType = "Comic")
        assertTrue(HomeShelf.WESTERN.matches(western))
        assertTrue(HomeShelf.WESTERN.matches(manga().copy(publicationType = "Graphic Novel")))
        HomeShelf.dynamicPool.filterNot { it in listOf(HomeShelf.WESTERN, HomeShelf.DISCOVERY) }.forEach { shelf ->
            assertFalse(shelf.name, shelf.matches(western))
        }
        assertTrue(HomeShelf.ACTION.matches(western.copy(genres = western.genres + "Action")))
        assertFalse(HomeShelf.SEINEN.matches(western.copy(genres = western.genres + "Action")))
        assertTrue(HomeShelf.ACTION.matches(manga("Action; Adventure").copy(publicationType = "Manhwa")))
        assertFalse(HomeShelf.ACTION.matches(manga().copy(publicationType = "Action")))
    }
}
