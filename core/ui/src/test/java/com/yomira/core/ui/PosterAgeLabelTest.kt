package com.yomira.core.ui

import com.yomira.core.model.ContentRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterAgeLabelTest {
    @Test fun `safe artwork has no badge`() {
        assertNull(posterAgeLabel(ContentRating.SAFE))
    }

    @Test fun `each adult tier gets its own compact age label`() {
        // v8.2.0 tier model: MATURE is the 16+ themed tier, EXPLICIT the 18+ one.
        assertEquals("16+", posterAgeLabel(ContentRating.MATURE))
        assertEquals("18+", posterAgeLabel(ContentRating.EXPLICIT))
    }
}
