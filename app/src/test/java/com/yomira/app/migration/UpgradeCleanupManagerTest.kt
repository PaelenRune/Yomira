package com.yomira.app.migration

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UpgradeCleanupManagerTest {
    @Test fun `upgrade runs once including concurrent calls and notice survives restart until dismissed`() = runTest {
        val state = MemoryState(20)
        var purges = 0
        val manager = UpgradeCleanupManager(state, { purges++ }, { false })
        List(8) { async { manager.run() } }.awaitAll()
        assertEquals(1, purges)
        assertEquals(21, state.version)
        val restarted = UpgradeCleanupManager(state, { purges++ }, { true })
        restarted.run()
        assertTrue(restarted.noticePending())
        assertTrue(shouldShowUpgradeNotice(restarted.noticePending(), onboardingLoading = false, repoDialogVisible = false))
        assertFalse(shouldShowUpgradeNotice(restarted.noticePending(), onboardingLoading = true, repoDialogVisible = false))
        assertFalse(shouldShowUpgradeNotice(restarted.noticePending(), onboardingLoading = false, repoDialogVisible = true))
        assertTrue(restarted.noticePending())
        restarted.dismissNotice()
        restarted.run()
        assertFalse(restarted.noticePending())
        assertFalse(shouldShowUpgradeNotice(restarted.noticePending(), onboardingLoading = false, repoDialogVisible = false))
        assertEquals(1, purges)
    }

    @Test fun `markerless old installation migrates but fresh install is unaffected`() = runTest {
        for (upgraded in listOf(false, true)) {
            val state = MemoryState()
            var purges = 0
            val manager = UpgradeCleanupManager(state, { purges++ }, { upgraded })
            manager.run()
            assertEquals(if (upgraded) 1 else 0, purges)
            assertEquals(upgraded, state.pending)
            assertEquals(21, state.version)
            manager.run()
            assertEquals(if (upgraded) 1 else 0, purges)
        }
    }

    @Test fun `failure leaves marker unset and retry repeats safe cleanup`() = runTest {
        val state = MemoryState(19)
        var attempts = 0
        val manager = UpgradeCleanupManager(state, { if (++attempts == 1) error("disk full") }, { false })
        try { manager.run(); fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals(19, state.version)
        assertFalse(state.pending)
        manager.run()
        assertEquals(2, attempts)
        assertEquals(21, state.version)
        assertTrue(state.pending)
    }

    @Test fun `same or newer marker never purges and future updates do not repeat migration`() = runTest {
        for (version in listOf(21, 22)) {
            val state = MemoryState(version)
            UpgradeCleanupManager(state, { error("Must not purge") }, { true }).run()
            assertEquals(version, state.version)
            assertFalse(state.pending)
        }
        val state = MemoryState(21)
        UpgradeCleanupManager(state, { error("Must not purge") }, { true }, version = 22).run()
        assertEquals(22, state.version)
    }

    private class MemoryState(var version: Int? = null) : UpgradeStateStore {
        var pending = false
        override fun lastSeenVersion() = version
        override fun noticePending() = pending
        override fun complete(version: Int, migrated: Boolean) { this.version = version; if (migrated) pending = true }
        override fun dismissNotice() { pending = false }
    }
}
