package com.yomira.domain.sync

import com.yomira.data.sync.SyncPayload

/** Timestamp-based LWW registers; explicit removal records survive merges. */
class ConflictResolutionEngine {
    fun merge(
        local: SyncPayload,
        remote: SyncPayload?,
        mergedAt: Long = System.currentTimeMillis(),
    ): SyncPayload {
        if (remote == null) return local.copy(
            schemaVersion = SyncPayload.CURRENT_SCHEMA_VERSION,
            version = local.version + 1,
            generatedAt = mergedAt.coerceAtLeast(0),
        ).normalized()

        // A content-based tie-break remains stable after another device republishes the document.
        val localSettings = local.settings
        val remoteSettings = remote.settings
        val remoteWinsTie = remoteSettings.toString() > localSettings.toString()
        return SyncPayload(
            deviceId = local.deviceId,
            version = maxOf(local.version, remote.version) + 1,
            generatedAt = mergedAt.coerceAtLeast(0),
            library = lww(local.library, remote.library, { it.mangaId }, { it.updatedAt }),
            readingProgress = lww(local.readingProgress, remote.readingProgress,
                { it.mangaId }, { it.lastReadAt }),
            readingHistory = lww(local.readingHistory, remote.readingHistory,
                { it.mangaId to it.chapterId }, { it.lastReadAt }),
            extensions = lww(local.extensions, remote.extensions,
                { it.packageId }, { it.updatedAt }),
            settings = when {
                localSettings == null -> remoteSettings
                remoteSettings == null -> localSettings
                remoteSettings.updatedAt > localSettings.updatedAt -> remoteSettings
                remoteSettings.updatedAt == localSettings.updatedAt && remoteWinsTie -> remoteSettings
                else -> localSettings
            },
        ).normalized()
    }

    private fun <T, K> lww(
        local: List<T>, remote: List<T>, key: (T) -> K, time: (T) -> Long,
    ): List<T> {
        val result = local.associateBy(key).toMutableMap()
        remote.forEach { incoming ->
            val existing = result[key(incoming)]
            if (existing == null || time(incoming) > time(existing) ||
                (time(incoming) == time(existing) && incoming.toString() > existing.toString())) result[key(incoming)] = incoming
        }
        return result.values.toList()
    }
}
