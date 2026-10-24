package com.yomira.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PagingTest {
    @Test
    fun `page window maps 1-based pages onto REST offsets`() {
        assertEquals(0, PageWindow(1, 25).offset)
        assertEquals(25, PageWindow(2, 25).offset)
        assertEquals(75, PageWindow(4, 25).offset)
        assertEquals(PageWindow(3, 25), PageWindow(1, 25).next().next())
    }

    @Test
    fun `offset windows resume on the same page boundary`() {
        assertEquals(PageWindow(2, 25), PageWindow.fromOffset(25, 25))
        assertEquals(PageWindow(3, 25), PageWindow.fromOffset(50, 25))
        // A partial final window still belongs to the page that contains the offset.
        assertEquals(PageWindow(3, 25), PageWindow.fromOffset(61, 25))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero page is rejected`() {
        PageWindow(0, 25)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative offset is rejected`() {
        PageWindow.fromOffset(-1, 25)
    }

    @Test
    fun `page size defaults to the product contract of 25`() {
        assertEquals(25, PageWindow.DEFAULT_PAGE_SIZE)
        assertEquals(25, PageWindow(2).offset)
    }
}
