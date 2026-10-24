package com.yomira.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WesternComicsTest {
    private fun manga(
        title: String,
        genres: List<String> = emptyList(),
        altTitles: List<String> = emptyList(),
        score: Double? = null,
        id: String = "source:${title.lowercase().replace(" ", "-")}",
    ) = Manga(
        id = id,
        title = title,
        altTitles = altTitles,
        genres = genres,
        score = score,
        sourceId = "source",
    )

    @Test
    fun `persisted western genre marker is recognised`() {
        assertTrue(WesternComics.matches(manga("Batman", listOf("Superhero", WesternComics.MARKER))))
    }

    @Test
    fun `marvel dc image and dark horse publishers are promoted`() {
        listOf("Marvel", "DC Comics", "Image Comics", "Dark Horse Comics", "Vertigo").forEach { publisher ->
            assertTrue(
                "Expected $publisher to be a western publisher",
                WesternComics.matches(
                    genres = listOf("Superhero"),
                    publicationType = null,
                    publisher = publisher,
                    titles = listOf("Unrelated Series"),
                ),
            )
        }
    }

    @Test
    fun `comic publication type marks an English original even without a publisher`() {
        assertTrue(
            WesternComics.matches(
                genres = emptyList(),
                publicationType = "Comic",
                publisher = null,
                titles = listOf("Random Graphic Novel"),
            ),
        )
    }

    @Test
    fun `marquee titles are recognised by token not by loose substring`() {
        assertTrue(WesternComics.matches(manga("The Amazing Spider-Man", listOf("Action"))))
        assertTrue(WesternComics.matches(manga("X-Men: Days of Future Past")))
        assertTrue(WesternComics.matches(manga("Invincible")))
        assertFalse("Vinland Saga must stay on the manga shelf", WesternComics.matches(manga("Vinland Saga")))
        assertFalse("A generic Boys title is not The Boys", WesternComics.matches(manga("Boys Run the Riot")))
        // `Saga` is exact-only because it is also a common manga-title token.
        assertTrue(WesternComics.matches(manga("Saga")))
    }

    @Test
    fun `spotlight opens with the curated marquee order and then scores`() {
        val records = listOf(
            manga("Saga", listOf(WesternComics.MARKER), score = 8.0),
            manga("Hellboy", listOf(WesternComics.MARKER), score = 8.5),
            manga("Batman", listOf(WesternComics.MARKER), score = 7.0),
            manga("Spider-Man", listOf(WesternComics.MARKER), score = 9.0),
            manga("Vinland Saga", listOf("Action"), score = 10.0),
        )
        val spotlight = WesternComics.spotlight(records)
        assertEquals(listOf("Spider-Man", "Batman", "Hellboy", "Saga"), spotlight.map { it.title })
        assertFalse(spotlight.any { it.title == "Vinland Saga" })
    }

    @Test
    fun `tag appends the marker exactly once`() {
        assertEquals(listOf("Action", WesternComics.MARKER), WesternComics.tag(listOf("Action")))
        assertEquals(listOf("Action", WesternComics.MARKER), WesternComics.tag(listOf("Action", WesternComics.MARKER)))
    }

    @Test
    fun `spotlight title is the exact product copy`() {
        assertEquals("🇺🇸 Amerikan & Batı Çizgi Roman Dünyası", WesternComics.SPOTLIGHT_TITLE)
    }
}
