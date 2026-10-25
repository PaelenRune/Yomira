package com.yomira.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelProgressionTest {
    @Test fun `level one starts at zero xp`() {
        val state = LevelProgression.stateForXp(0)
        assertEquals(1, state.level)
        assertEquals(0, state.xpIntoLevel)
        assertEquals("Meraklı Çırak", state.titleTr)
    }

    @Test fun `level twelve is the fantasy voyager`() {
        val state = LevelProgression.stateForXp(LevelProgression.xpRequiredForLevel(12))
        assertEquals(12, state.level)
        assertEquals("Fantastik Gezgin", state.titleTr)
        assertEquals("Fantasy Voyager", state.titleEn)
    }

    @Test fun `progress is bounded and remaining xp is non-negative`() {
        val state = LevelProgression.stateForXp(LevelProgression.xpRequiredForLevel(3) + 10)
        assertEquals(3, state.level)
        assertTrue(state.progress in 0f..1f)
        assertTrue(state.xpRemaining >= 0)
    }

    @Test fun `xp inside level never exceeds its span`() {
        val start = LevelProgression.xpRequiredForLevel(5)
        val next = LevelProgression.xpRequiredForLevel(6)
        val state = LevelProgression.stateForXp(start + (next - start) - 1)
        assertEquals(5, state.level)
        assertEquals(next - start - 1, state.xpIntoLevel)
    }

    @Test fun `negative xp is clamped`() {
        assertEquals(1, LevelProgression.levelForXp(-500))
        assertEquals(1, LevelProgression.stateForXp(-1).level)
    }
}

