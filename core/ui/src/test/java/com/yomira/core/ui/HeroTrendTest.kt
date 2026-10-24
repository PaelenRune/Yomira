package com.yomira.core.ui

import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class HeroTrendTest {
    private fun manga(id: String) = Manga(id, "Series $id", sourceId = "seed")

    @Test fun `hero lead rotates once per iso week`() {
        val items = listOf("a", "b", "c", "d").map { manga(it) }
        val weekTwo = weeklyTrendOrder(items, LocalDate.of(2026, 1, 5))
        val weekThree = weeklyTrendOrder(items, LocalDate.of(2026, 1, 12))
        // The showcase keeps every trending series but leads with a different card.
        assertEquals(items.size, weekTwo.size)
        assertEquals(items.toSet(), weekTwo.toSet())
        assertEquals(items.toSet(), weekThree.toSet())
        assertNotEquals(weekTwo.first(), weekThree.first())
    }

    @Test fun `same week always renders the same order`() {
        val items = listOf("a", "b", "c").map { manga(it) }
        assertEquals(
            weeklyTrendOrder(items, LocalDate.of(2026, 2, 3)),
            weeklyTrendOrder(items, LocalDate.of(2026, 2, 4)),
        )
    }

    @Test fun `single series hero stays put`() {
        val items = listOf(manga("solo"))
        assertEquals(items, weeklyTrendOrder(items, LocalDate.of(2026, 2, 3)))
    }
}