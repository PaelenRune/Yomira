package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.HomeShelf
import com.yomira.core.model.Manga
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRailsTest {
    private fun manga(id: String, genre: String = "Action", explicit: Boolean = false) = Manga(
        id = id, title = id, genres = listOf(genre), sourceId = "test",
        contentRating = if (explicit) ContentRating.EXPLICIT else ContentRating.SAFE,
    )

    @Test fun `rails retain selected order even when genre pools are empty`() {
        val selection = HomeShelf.select(Random(23))
        val rails = buildHomeRails(emptyList(), emptyList(), emptyList(), selection, emptySet(), emptySet(), 3)
        assertEquals(selection, rails.map { it.shelf })
        assertEquals(7, rails.size)
        assertTrue(rails.all { it.items.isEmpty() })
    }

    @Test fun `all rails exclude explicit records and deduplicate persisted discovery rows`() {
        val safe = manga("safe")
        val adult = manga("adult", explicit = true)
        val rails = buildHomeRails(
            listOf(safe, adult, safe), listOf(adult), listOf(adult, safe),
            HomeShelf.entries, emptySet(), emptySet(), 8,
        )
        assertTrue(rails.all { rail -> rail.items.none { it.contentRating == ContentRating.EXPLICIT } })
        assertTrue(rails.all { rail -> rail.items.size == rail.items.distinctBy { it.id }.size })
    }

    @Test fun `genre shelves never backfill with unrelated titles`() {
        val rails = buildHomeRails(
            listOf(manga("romance", "Romance"), manga("action")), emptyList(), emptyList(),
            listOf(HomeShelf.ROMANCE, HomeShelf.ISEKAI), emptySet(), emptySet(), 8,
        )
        assertEquals(listOf("romance"), rails.first().items.map { it.id })
        assertTrue(rails.last().items.isEmpty())
    }

    @Test fun `selection and card order stay stable across identical snapshots`() {
        val catalog = (1..50).map { manga("series-$it") }
        val selection = HomeShelf.select(Random(7))
        fun rails() = buildHomeRails(catalog, emptyList(), emptyList(), selection, emptySet(), emptySet(), 5)
        assertEquals(rails(), rails())
        assertTrue(rails().all { it.items.size <= 14 })
    }

    @Test fun `newly discovered rows lead discovery and personalized rail survives fully read catalog`() {
        val known = manga("known")
        val fresh = manga("fresh")
        val rails = buildHomeRails(
            listOf(known, fresh), emptyList(), listOf(fresh),
            listOf(HomeShelf.PERSONAL, HomeShelf.DISCOVERY), setOf("known", "fresh"), emptySet(), 12,
        )
        assertFalse(rails.first().items.isEmpty())
        assertEquals("fresh", rails.last().items.first().id)
    }

    @Test fun `four affinity slots evolve with reads and saves while discovery is reserved`() {
        val catalog = listOf(
            manga("murim-1", "Murim"), manga("murim-2", "Martial Arts"),
            manga("seinen", "Seinen"), manga("isekai", "Isekai"),
        )
        fun shelves(read: Set<String>, saved: Set<String> = emptySet(), seed: Int = 1) =
            buildHomeRails(catalog, emptyList(), emptyList(), readIds = read, savedIds = saved, seed = seed)
                .map { it.shelf }
        val cold = shelves(emptySet())
        assertEquals(cold, shelves(emptySet(), seed = 500))
        val learned = shelves(setOf("murim-1", "murim-2"), setOf("seinen", "isekai"))
        assertEquals(HomeShelf.fixed, learned.take(2))
        assertEquals(listOf(HomeShelf.MARTIAL_ARTS, HomeShelf.ISEKAI, HomeShelf.SEINEN), learned.drop(2).take(3))
        assertEquals(7, learned.size)
        assertEquals(7, learned.distinct().size)
        assertEquals(HomeShelf.DISCOVERY, learned.last())
        assertEquals(HomeShelf.SEINEN, shelves(setOf("seinen"))[2])
    }

    @Test fun `affinity counts each read or saved title once and ignores explicit history`() {
        val catalog = listOf(
            manga("murim", "Murim"), manga("seinen-1", "Seinen"), manga("seinen-2", "Seinen"),
            manga("adult", "Isekai", explicit = true),
        )
        val shelves = adaptiveHomeShelves(catalog + catalog, setOf("murim", "seinen-1", "seinen-2", "adult"), setOf("murim"))
        assertEquals(HomeShelf.SEINEN, shelves[2])
        assertEquals(HomeShelf.MARTIAL_ARTS, shelves[3])
        assertFalse(HomeShelf.ISEKAI in shelves)
    }
}
