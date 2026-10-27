package com.yomira.domain.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionSyncTrackerTest {
    @Test fun `initial replay and unchanged database emissions do not resync`() {
        val tracker = CompletionSyncTracker<String>()
        assertFalse(tracker.observe(mapOf("c" to 100L)))
        assertFalse(tracker.observe(mapOf("c" to 100L)))
        assertTrue(tracker.observe(mapOf("c" to 200L)))
        assertFalse(tracker.observe(mapOf("c" to 200L)))
    }

    @Test fun `new completions and recompleting a reread trigger sync`() {
        val tracker = CompletionSyncTracker<String>()
        assertFalse(tracker.observe(emptyMap()))
        assertTrue(tracker.observe(mapOf("c" to 100L)))
        assertFalse(tracker.observe(emptyMap()))
        assertTrue(tracker.observe(mapOf("c" to 200L)))
    }
}
