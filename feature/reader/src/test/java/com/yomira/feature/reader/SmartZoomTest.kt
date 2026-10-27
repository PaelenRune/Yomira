package com.yomira.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartZoomTest {
    @Test fun `unzoomed page resets pan`() {
        assertEquals(Offset.Zero, clampPan(Offset(100f, -200f), 1f, IntSize(400, 800)))
        assertEquals(Offset.Zero, clampPan(Offset(100f, -200f), .5f, IntSize(400, 800)))
    }

    @Test fun `zoomed page clamps both axes to visible bounds`() {
        assertEquals(Offset(300f, -600f), clampPan(Offset(1000f, -2000f), 2.5f, IntSize(400, 800)))
        assertEquals(Offset(-300f, 600f), clampPan(Offset(-1000f, 2000f), 2.5f, IntSize(400, 800)))
    }

    @Test fun `in bounds pan preserves the focal position`() {
        val focal = Offset(50f, -100f)
        assertEquals(focal, clampPan(focal, 2.5f, IntSize(400, 800)))
    }

    @Test fun `unknown page dimensions cannot leave a stale pan`() {
        val pan = clampPan(Offset(100f, -200f), 5f, IntSize.Zero)
        assertEquals(0f, pan.x, 0f)
        assertEquals(0f, pan.y, 0f)
    }

    @Test fun `resize reclamps the previous pan`() {
        val before = clampPan(Offset(600f, 300f), 2.5f, IntSize(800, 400))
        assertEquals(Offset(300f, 300f), clampPan(before, 2.5f, IntSize(400, 800)))
    }
}
