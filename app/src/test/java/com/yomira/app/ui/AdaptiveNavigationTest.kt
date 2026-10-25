package com.yomira.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boundary table of the adaptive shell.
 *
 * The 600dp line decides whether the app shows a bottom bar (phone) or a left
 * navigation rail plus master-detail (tablet/foldable). Keeping the policy pure
 * lets CI lock that behaviour without an emulator.
 */
class AdaptiveNavigationTest {

    @Test
    fun `phones below 600dp use one pane and the bottom bar`() {
        listOf(320f, 360f, 411f, 599f, 599.9f).forEach { width ->
            assertEquals("width=$width", AdaptiveNavigationMode.COMPACT, AdaptiveNavigationPolicy.modeFor(width))
            assertFalse("width=$width", AdaptiveNavigationPolicy.usesNavigationRail(width))
            assertTrue("width=$width", AdaptiveNavigationPolicy.usesBottomNavigationBar(width))
            assertEquals("width=$width", 1, AdaptiveNavigationPolicy.paneCount(width))
            assertFalse("width=$width", AdaptiveNavigationPolicy.usesTwoPaneDetail(width))
        }
    }

    @Test
    fun `600dp is the inclusive tablet breakpoint`() {
        assertEquals(AdaptiveNavigationMode.MEDIUM, AdaptiveNavigationPolicy.modeFor(600f))
        assertTrue(AdaptiveNavigationPolicy.usesNavigationRail(600f))
        assertFalse(AdaptiveNavigationPolicy.usesBottomNavigationBar(600f))
        assertEquals(2, AdaptiveNavigationPolicy.paneCount(600f))
        assertTrue(AdaptiveNavigationPolicy.usesTwoPaneDetail(600f))
    }

    @Test
    fun `840dp and above open the expanded tablet layout`() {
        assertEquals(AdaptiveNavigationMode.EXPANDED, AdaptiveNavigationPolicy.modeFor(840f))
        assertEquals(AdaptiveNavigationMode.EXPANDED, AdaptiveNavigationPolicy.modeFor(1280f))
        assertEquals(AdaptiveNavigationMode.EXPANDED, AdaptiveNavigationPolicy.modeFor(2560f))
        assertTrue(AdaptiveNavigationPolicy.usesNavigationRail(2560f))
        assertEquals(2, AdaptiveNavigationPolicy.paneCount(2560f))
    }

    @Test
    fun `only one navigation affordance is visible at any width`() {
        listOf(320f, 599.9f, 600f, 839f, 840f, 1600f).forEach { width ->
            val rail = AdaptiveNavigationPolicy.usesNavigationRail(width)
            val bottom = AdaptiveNavigationPolicy.usesBottomNavigationBar(width)
            assertTrue("width=$width must have one navigation surface", rail xor bottom)
        }
    }

    @Test
    fun `catalog columns follow the bucket instead of shrinking tablet covers`() {
        assertEquals(1, AdaptiveNavigationPolicy.catalogColumns(411f, preferred = 4))
        assertEquals(2, AdaptiveNavigationPolicy.catalogColumns(700f, preferred = 1))
        assertEquals(4, AdaptiveNavigationPolicy.catalogColumns(700f, preferred = 6))
        assertEquals(4, AdaptiveNavigationPolicy.catalogColumns(1200f, preferred = 2))
        assertEquals(6, AdaptiveNavigationPolicy.catalogColumns(1200f, preferred = 6))
    }
}
