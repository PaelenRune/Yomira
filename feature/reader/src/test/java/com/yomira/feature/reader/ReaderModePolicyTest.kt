package com.yomira.feature.reader

import com.yomira.core.model.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModePolicyTest {

    @Test
    fun `webtoon chapters always use the vertical strip reader`() {
        assertEquals(
            ReaderPipeline.WEBTOON,
            ReaderModePolicy.pipeline(ReadingMode.WEBTOON, ReaderPreferences(guidedView = true, dualPage = true, wideViewport = true)),
        )
    }

    @Test
    fun `guided view wins on paged modes when enabled`() {
        assertEquals(
            ReaderPipeline.GUIDED,
            ReaderModePolicy.pipeline(ReadingMode.MANGA_RTL, ReaderPreferences(guidedView = true)),
        )
        assertEquals(
            ReaderPipeline.GUIDED,
            ReaderModePolicy.pipeline(ReadingMode.WESTERN_LTR, ReaderPreferences(guidedView = true, dualPage = true, wideViewport = true)),
        )
    }

    @Test
    fun `dual page only engages on wide viewports`() {
        assertEquals(
            ReaderPipeline.DUAL,
            ReaderModePolicy.pipeline(ReadingMode.WESTERN_LTR, ReaderPreferences(dualPage = true, wideViewport = true)),
        )
        assertEquals(
            ReaderPipeline.PAGER,
            ReaderModePolicy.pipeline(ReadingMode.WESTERN_LTR, ReaderPreferences(dualPage = true, wideViewport = false)),
        )
    }

    @Test
    fun `default is the plain pager`() {
        assertEquals(ReaderPipeline.PAGER, ReaderModePolicy.pipeline(ReadingMode.MANGA_RTL, ReaderPreferences()))
    }

    @Test
    fun `reading direction follows the mode`() {
        assertTrue(ReaderModePolicy.readsRightToLeft(ReadingMode.MANGA_RTL))
        assertFalse(ReaderModePolicy.readsRightToLeft(ReadingMode.WESTERN_LTR))
        assertFalse(ReaderModePolicy.readsRightToLeft(ReadingMode.WEBTOON))
    }

    @Test
    fun `capability guards match the modes`() {
        assertTrue(ReaderModePolicy.supportsGuidedView(ReadingMode.MANGA_RTL))
        assertFalse(ReaderModePolicy.supportsGuidedView(ReadingMode.WEBTOON))
        assertTrue(ReaderModePolicy.supportsDualPage(ReadingMode.WESTERN_LTR, wideViewport = true))
        assertFalse(ReaderModePolicy.supportsDualPage(ReadingMode.WEBTOON, wideViewport = true))
        assertTrue(ReaderModePolicy.shouldCleanSeams(ReadingMode.WEBTOON))
        assertFalse(ReaderModePolicy.shouldCleanSeams(ReadingMode.MANGA_RTL))
    }
}