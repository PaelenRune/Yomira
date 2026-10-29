package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the library search scope: reading progress counts as membership before
 * saving. A series the user is reading (the Home resume capsule proves it) must
 * stay findable from the library tab search — QA device evidence: query
 * "One Piece" returned "Sonuç bulunamadı" while the series was being read but
 * had never been added to the shelf.
 */
class LibrarySearchScopeTest {

    private fun manga(id: String, title: String) = Manga(
        id = id, title = title, sourceId = "test", contentRating = ContentRating.SAFE,
    )

    @Test
    fun `blank query keeps the quiet shelf exactly as-is`() {
        val shelf = listOf(manga("saved", "Batman"))
        val beyond = listOf(manga("reading", "One Piece"))
        assertEquals(shelf, searchableCatalog(shelf, beyond, "  "))
    }

    @Test
    fun `typed query also searches the read-but-unsaved series`() {
        val shelf = listOf(manga("saved", "Batman"))
        val beyond = listOf(manga("reading", "One Piece"))
        val base = searchableCatalog(shelf, beyond, "One Piece")
        assertEquals(2, base.size)
        assertEquals(listOf("One Piece"), searchLocalCatalog(base, "One Piece").map { it.title })
    }

    @Test
    fun `beyond entries duplicate nothing already on the shelf`() {
        val shelf = listOf(manga("both", "Batman"), manga("saved", "Berserk"))
        val beyond = listOf(manga("both", "Batman (duplicate id)"))
        val base = searchableCatalog(shelf, beyond, "Bat")
        assertEquals(2, base.size)
        // The shelf's own entry wins; the duplicate id never enters the set.
        assertEquals("Batman", base.first { it.id == "both" }.title)
    }

    @Test
    fun `search beyond does not manufacture matches that do not exist`() {
        val base = searchableCatalog(
            listOf(manga("saved", "Batman")),
            listOf(manga("reading", "One Piece")),
            "Frieren",
        )
        assertTrue(searchLocalCatalog(base, "Frieren").isEmpty())
    }
}
