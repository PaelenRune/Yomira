package com.yomira.core.ui

import com.yomira.core.model.ContentRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterAgeLabelTest {
    @Test fun `safe artwork has no badge`() {
        assertNull(posterAgeLabel(ContentRating.SAFE))
    }

    @Test fun `adult artwork only gets a compact age label`() {
        assertEquals("+18", posterAgeLabel(ContentRating.MATURE))
        assertEquals("+18", posterAgeLabel(ContentRating.EXPLICIT))
    }
}
