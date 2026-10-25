package com.yomira.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveReadingTimerTest {
    @Test fun `idle gap longer than a minute is not counted`() {
        val timer = ActiveReadingTimer()
        timer.start(0)
        assertEquals(5_000, timer.interaction(5_000))
        // Phone left open for ten minutes: the gap is dropped.
        assertEquals(0, timer.interaction(605_000))
        assertEquals(10_000, timer.interaction(615_000))
        assertEquals(15_000, timer.drainActiveMillis())
        assertEquals(0, timer.drainActiveMillis())
    }

    @Test fun `pause drops the anchor without crediting the background gap`() {
        val timer = ActiveReadingTimer()
        timer.start(1_000)
        timer.interaction(6_000)
        timer.pause()
        assertEquals(0, timer.interaction(60_000))
        assertEquals(5_000, timer.drainActiveMillis())
    }

    @Test fun `pending read is non destructive`() {
        val timer = ActiveReadingTimer()
        timer.start(0)
        timer.interaction(3_000)
        assertEquals(3_000, timer.pendingMillis())
        assertEquals(3_000, timer.drainActiveMillis())
    }

    @Test fun `non monotonic clock never subtracts time`() {
        val timer = ActiveReadingTimer()
        timer.start(10_000)
        assertEquals(0, timer.interaction(9_000))
        assertEquals(0, timer.drainActiveMillis())
    }
}
