package com.yomira.domain.sync

/** Suppresses initial database replay and duplicate completion emissions after a sync import. */
class CompletionSyncTracker<K> {
    private var previous: Map<K, Long>? = null

    fun observe(completed: Map<K, Long>): Boolean {
        val before = previous
        previous = completed.toMap()
        return before != null && completed.any { (id, timestamp) -> timestamp > (before[id] ?: -1) }
    }
}
