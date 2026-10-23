package com.yomira.domain.sync

import com.yomira.data.sync.LibrarySyncEntry
import com.yomira.data.sync.ReadingProgressSyncEntry
import com.yomira.data.sync.SyncPayload
import com.yomira.data.sync.UserSettingsSync

/**
 * Deterministic "Max Progress Wins" merger for two devices.
 *
 * Rules:
 * 1. Library entries are unioned by mangaId. A title stays in the library if either
 *    device has it; the category comes from the newest entry (local on a tie).
 * 2. Reading progress keeps the furthest chapter, then the furthest page. Only an
 *    exact progress tie is broken by `lastReadAt`, so a device with a fast clock can
 *    never move the user backwards. Page count is never used to compare chapters.
 * 3. User settings are atomic: the whole settings snapshot with the newest
 *    `updatedAt` wins. Partial merging of preferences would create impossible states.
 *
 * The merger is commutative for content (all differences use timestamps or total
 * ordering), except for the payload-level deviceId/version which identify the device
 * that produced the merged document.
 */
class ConflictResolutionEngine {

    fun merge(
        local: SyncPayload,
        remote: SyncPayload?,
        mergedAt: Long = System.currentTimeMillis(),
    ): SyncPayload {
        val now = mergedAt.coerceAtLeast(0L)
        if (remote == null) {
            return local.copy(
                schemaVersion = SyncPayload.CURRENT_SCHEMA_VERSION,
                version = local.version + 1,
                generatedAt = now,
            ).normalized()
        }

        return SyncPayload(
            schemaVersion = SyncPayload.CURRENT_SCHEMA_VERSION,
            deviceId = local.deviceId,
            version = maxOf(local.version, remote.version) + 1,
            generatedAt = now,
            library = mergeLibrary(local.library, remote.library),
            readingProgress = mergeReadingProgress(local.readingProgress, remote.readingProgress),
            settings = mergeSettings(local.settings, remote.settings),
        ).normalized()
    }

    private fun mergeLibrary(
        local: List<LibrarySyncEntry>,
        remote: List<LibrarySyncEntry>,
    ): List<LibrarySyncEntry> {
        val merged = LinkedHashMap<String, LibrarySyncEntry>(local.size + remote.size)
        for (entry in local) merged[entry.mangaId] = entry
        for (entry in remote) {
            val existing = merged[entry.mangaId]
            merged[entry.mangaId] = if (existing == null) entry else mergeLibraryEntry(existing, entry)
        }
        return merged.values.sortedBy(LibrarySyncEntry::mangaId)
    }

    private fun mergeLibraryEntry(
        left: LibrarySyncEntry,
        right: LibrarySyncEntry,
    ): LibrarySyncEntry {
        val categorySource = when {
            left.category == null -> right
            right.category == null -> left
            right.updatedAt > left.updatedAt -> right
            else -> left
        }
        return LibrarySyncEntry(
            mangaId = left.mangaId,
            inLibrary = left.inLibrary || right.inLibrary,
            category = categorySource.category,
            updatedAt = maxOf(left.updatedAt, right.updatedAt),
        )
    }

    private fun mergeReadingProgress(
        local: List<ReadingProgressSyncEntry>,
        remote: List<ReadingProgressSyncEntry>,
    ): List<ReadingProgressSyncEntry> {
        val merged = LinkedHashMap<String, ReadingProgressSyncEntry>(local.size + remote.size)
        for (entry in local) merged[entry.mangaId] = entry
        for (entry in remote) {
            val existing = merged[entry.mangaId]
            merged[entry.mangaId] = if (existing == null) {
                entry
            } else {
                furthestProgress(existing, entry)
            }
        }
        return merged.values.sortedBy(ReadingProgressSyncEntry::mangaId)
    }

    private fun furthestProgress(
        left: ReadingProgressSyncEntry,
        right: ReadingProgressSyncEntry,
    ): ReadingProgressSyncEntry {
        val chapter = left.chapterNumber.compareTo(right.chapterNumber)
        if (chapter != 0) return if (chapter > 0) left else right

        val page = left.pageIndex.compareTo(right.pageIndex)
        if (page != 0) return if (page > 0) left else right

        // Same page: keep the most recently opened observation.
        return if (left.lastReadAt >= right.lastReadAt) left else right
    }

    private fun mergeSettings(
        local: UserSettingsSync?,
        remote: UserSettingsSync?,
    ): UserSettingsSync? = when {
        local == null -> remote
        remote == null -> local
        remote.updatedAt > local.updatedAt -> remote
        else -> local
    }
}
