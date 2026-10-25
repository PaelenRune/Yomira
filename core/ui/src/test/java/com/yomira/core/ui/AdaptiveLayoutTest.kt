package com.yomira.core.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the phone vs tablet/foldable contract of the adaptive shell. */
class AdaptiveLayoutTest {

    @Test
    fun `phone widths keep the bottom bar and a single pane`() {
        for (width in listOf(0f, 320f, 360f, 411f, 480f, 599f, 599.5f)) {
            val layout = AdaptiveLayout.forWidth(width)
            assertEquals("width $width", WindowWidthClass.COMPACT, layout.widthClass)
            assertEquals(AdaptiveNavigation.BOTTOM_BAR, layout.navigation)
            assertTrue(layout.useBottomBar)
            assertFalse(layout.twoPane)
            assertEquals(0, layout.detailListPaneWidthDp)
        }
    }

    @Test
    fun `tablet breakpoint is exactly 600dp and enables rail plus two panes`() {
        val phone = AdaptiveLayout.forWidth(599.9f)
        val tablet = AdaptiveLayout.forWidth(600f)
        assertEquals(WindowWidthClass.COMPACT, phone.widthClass)
        assertEquals(WindowWidthClass.MEDIUM, tablet.widthClass)
        assertTrue(tablet.twoPane)
        assertTrue(tablet.useNavigationRail)
        assertFalse(phone.twoPane)
    }

    @Test
    fun `foldable and tablet widths stay on the rail through the expanded breakpoint`() {
        for (width in listOf(600f, 673f, 720f, 839f, 840f, 1024f, 1280f)) {
            val layout = AdaptiveLayout.forWidth(width)
            assertEquals(AdaptiveNavigation.NAVIGATION_RAIL, layout.navigation)
            assertTrue("width $width must stay two-pane", layout.twoPane)
        }
        assertEquals(WindowWidthClass.MEDIUM, AdaptiveLayout.forWidth(839f).widthClass)
        assertEquals(WindowWidthClass.EXPANDED, AdaptiveLayout.forWidth(840f).widthClass)
    }

    @Test
    fun `detail chapter pane stays usable and leaves room for the dossier`() {
        for (width in listOf(600f, 720f, 840f, 1024f, 1280f, 1600f)) {
            val layout = AdaptiveLayout.forWidth(width)
            assertTrue("width $width", layout.detailListPaneWidthDp in 280..420)
            assertTrue(
                "width $width leaves at least 260dp for the dossier",
                width - layout.detailListPaneWidthDp >= 260f,
            )
        }
    }

    @Test
    fun `catalogue columns never shrink as the window grows`() {
        val widths = listOf(320f, 420f, 600f, 840f, 1200f, 1600f)
        val columns = widths.map { AdaptiveLayout.forWidth(it).catalogueColumns }
        columns.zipWithNext { left, right ->
            assertTrue("$columns must be monotonic", right >= left)
        }
        assertEquals(2, AdaptiveLayout.forWidth(360f).catalogueColumns)
        assertEquals(3, AdaptiveLayout.forWidth(600f).catalogueColumns)
        assertEquals(4, AdaptiveLayout.forWidth(1280f).catalogueColumns)
    }

    @Test
    fun `navigation rail and two pane helpers agree with the breakpoint`() {
        org.junit.Assert.assertFalse(AdaptiveLayout.usesNavigationRail(480f))
        org.junit.Assert.assertTrue(AdaptiveLayout.usesNavigationRail(600f))
        org.junit.Assert.assertTrue(AdaptiveLayout.prefersTwoPane(600f))
        org.junit.Assert.assertFalse(AdaptiveLayout.prefersTwoPane(599f))
        assertEquals(WindowSizeClass.Compact, AdaptiveLayout.windowSizeClass(360.dp))
        assertEquals(WindowSizeClass.Medium, AdaptiveLayout.windowSizeClass(720.dp))
        assertEquals(WindowSizeClass.Expanded, AdaptiveLayout.windowSizeClass(1024.dp))
    }

    @Test
    fun `catalog columns respect the user preference inside the width class`() {
        assertEquals(2, AdaptiveLayout.catalogColumns(WindowSizeClass.Compact, 2))
        // A 4-column preference is capped: phones cannot render 4 legible posters.
        assertEquals(3, AdaptiveLayout.catalogColumns(WindowSizeClass.Compact, 4))
        assertEquals(3, AdaptiveLayout.catalogColumns(WindowSizeClass.Medium, 2))
        assertEquals(4, AdaptiveLayout.catalogColumns(WindowSizeClass.Medium, 4))
        assertEquals(4, AdaptiveLayout.catalogColumns(WindowSizeClass.Expanded, 2))
    }

    @Test
    fun `invalid negative widths downgrade to compact instead of crashing`() {
        val negative = AdaptiveLayout.forWidth(-120f)
        val infinite = AdaptiveLayout.forWidth(Float.POSITIVE_INFINITY)
        assertEquals(WindowWidthClass.COMPACT, negative.widthClass)
        assertFalse(negative.twoPane)
        // A non-finite measurement is a transient layout state, not a 4K monitor:
        // fall back to the compact layout instead of overflowing.
        assertEquals(WindowWidthClass.COMPACT, infinite.widthClass)
    }
}
