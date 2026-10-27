package com.yomira.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMemoryPolicyTest {
    @Test fun `large heaps cannot retain unlimited artwork and reader pages`() {
        assertEquals(32 * 1024 * 1024, ImageMemoryPolicy.artworkBytes(Long.MAX_VALUE))
        assertEquals(64 * 1024 * 1024, ImageMemoryPolicy.readerBytes(Long.MAX_VALUE))
    }

    @Test fun `combined caches stay within a quarter of a small heap`() {
        val heap = 128L * 1024 * 1024
        val total = ImageMemoryPolicy.artworkBytes(heap).toLong() + ImageMemoryPolicy.readerBytes(heap)
        assertTrue(total <= heap / 4)
        assertTrue(total > 0)
    }

    @Test fun `invalid budgets do not overflow`() {
        assertEquals(0, ImageMemoryPolicy.readerBytes(-1))
        assertEquals(0, ImageMemoryPolicy.artworkBytes(0))
    }
}
