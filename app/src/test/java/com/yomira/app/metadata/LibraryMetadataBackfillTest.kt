package com.yomira.app.metadata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LibraryMetadataBackfillTest {
    @Test fun rateLimitedAndCheckpointed() = runTest {
        val events = mutableListOf<String>()
        val runner = LibraryMetadataBackfill({ true }, { events += it; true },
            { id, ok -> events += "$id:$ok" }, { events += "pause" })
        assertTrue(runner.run(listOf("a", "b")))
        assertEquals(listOf("pause", "a", "a:true", "pause", "b", "b:true"), events)
    }
    @Test fun lockOrDataSaverPausesWithoutCheckpoint() = runTest {
        var allowed = true
        val runner = LibraryMetadataBackfill({ allowed }, { error("No network request") },
            { _, _ -> error("No checkpoint") }, { allowed = false })
        assertFalse(runner.run(listOf("a")))
    }
    @Test fun failedRecordDoesNotBlockRemainingLibrary() = runTest {
        val results = mutableListOf<Boolean>()
        val runner = LibraryMetadataBackfill({ true }, { if (it == "a") error("offline") else true },
            { _, success -> results += success }, {})
        assertTrue(runner.run(listOf("a", "b")))
        assertEquals(listOf(false, true), results)
    }
    @Test fun cancellationNeverCheckpointsOrContinues() = runTest {
        var calls = 0
        val runner = LibraryMetadataBackfill({ true }, { calls++; throw CancellationException() },
            { _, _ -> error("Interrupted") }, {})
        try { runner.run(listOf("a", "b")); fail("Cancellation lost") }
        catch (_: CancellationException) { assertEquals(1, calls) }
    }
}
