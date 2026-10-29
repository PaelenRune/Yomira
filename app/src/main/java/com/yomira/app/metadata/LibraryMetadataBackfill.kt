package com.yomira.app.metadata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** No Android dependencies: checkpoint only completed attempts, never interrupted requests. */
internal class LibraryMetadataBackfill(
    private val allowed: () -> Boolean,
    private val refresh: suspend (String) -> Boolean,
    private val checkpoint: (String, Boolean) -> Unit,
    private val pause: suspend () -> Unit = { delay(2_000) },
) {
    suspend fun run(ids: List<String>): Boolean {
        for (id in ids) {
            if (!allowed()) return false
            pause() // Also rate-limit the first request after a restart.
            if (!allowed()) return false
            val success = try { refresh(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            if (!allowed()) return false
            checkpoint(id, success)
        }
        return true
    }
}
