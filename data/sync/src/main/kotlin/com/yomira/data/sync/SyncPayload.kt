package com.yomira.data.sync

import kotlinx.serialization.Serializable

/**
 * The complete, media-free state that Yomira synchronises through Google Drive.
 *
 * Only metadata is transferred: library membership, reading position and user
 * preferences. Covers, chapter payloads, downloaded files and source responses are
 * intentionally absent, so a sync never consumes Google Drive media quota.
 *
 * All timestamps are UTC epoch milliseconds. Keep the field names stable: they are
 * the on-disk contract of `yomira_sync.json` and older clients must be able to read
 * data written by newer ones (unknown keys are ignored by [SyncPayloadCodec]).
 */
@Serializable
data class SyncPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Stable random UUID generated per installation, never a hardware identifier. */
    val deviceId: String,
    /** Logical revision; the merger always increments it. */
    val version: Long = 0L,
    /** Time this document was produced, in UTC epoch milliseconds. */
    val generatedAt: Long = 0L,
    val library: List<LibrarySyncEntry> = emptyList(),
    val readingProgress: List<ReadingProgressSyncEntry> = emptyList(),
    val settings: UserSettingsSync? = null,
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(version >= 0L) { "version must not be negative" }
        require(generatedAt >= 0L) { "generatedAt must not be negative" }
    }

    /**
     * Validates the invariants that cannot be expressed with field initialisers.
     * Called by [SyncPayloadCodec] after decoding and before encoding.
     */
    fun validate() {
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "Unsupported sync schema version: $schemaVersion"
        }
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(version >= 0L) { "version must not be negative" }
        require(generatedAt >= 0L) { "generatedAt must not be negative" }
        requireUniqueIds(library.map(LibrarySyncEntry::mangaId), "library")
        requireUniqueIds(readingProgress.map(ReadingProgressSyncEntry::mangaId), "readingProgress")
        settings?.validate()
    }

    /** Canonical ordering keeps the uploaded JSON deterministic across devices. */
    fun normalized(): SyncPayload = copy(
        library = library.sortedBy(LibrarySyncEntry::mangaId),
        readingProgress = readingProgress.sortedBy(ReadingProgressSyncEntry::mangaId),
    )

    private fun requireUniqueIds(ids: List<String>, section: String) {
        require(ids.size == ids.toSet().size) { "$section contains duplicate mangaId entries" }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/** Library membership. A missing entry means "not known by this device". */
@Serializable
data class LibrarySyncEntry(
    val mangaId: String,
    /** Union-merged: if any device has the title in its library, it stays in the library. */
    val inLibrary: Boolean = true,
    /** User category/tag, for example "Reading" or "Favourites". */
    val category: String? = null,
    /** Last time this entry changed locally, used to pick the winning category. */
    val updatedAt: Long = 0L,
) {
    init {
        require(mangaId.isNotBlank()) { "mangaId must not be blank" }
        require(category == null || category.isNotBlank()) { "category must be null or non-blank" }
        require(updatedAt >= 0L) { "updatedAt must not be negative" }
    }
}

/**
 * Reading position. The merger keeps the furthest chapter, then the furthest page
 * ("Max Progress Wins"), so a device with an older clock cannot roll progress back.
 */
@Serializable
data class ReadingProgressSyncEntry(
    val mangaId: String,
    val chapterNumber: Double,
    val pageIndex: Int,
    /** Last opened time, UTC epoch milliseconds. Only breaks exact progress ties. */
    val lastReadAt: Long = 0L,
) {
    init {
        require(mangaId.isNotBlank()) { "mangaId must not be blank" }
        require(chapterNumber.isFinite() && chapterNumber >= 0.0) {
            "chapterNumber must be finite and non-negative"
        }
        require(pageIndex >= 0) { "pageIndex must not be negative" }
        require(lastReadAt >= 0L) { "lastReadAt must not be negative" }
    }
}

/** User preferences, merged as a whole by their newest [updatedAt]. */
@Serializable
data class UserSettingsSync(
    val gridColumns: Int = DEFAULT_GRID_COLUMNS,
    val adultEnabled: Boolean = false,
    val theme: String = DEFAULT_THEME,
    /** Last time any preference changed, UTC epoch milliseconds. */
    val updatedAt: Long = 0L,
) {
    init {
        require(gridColumns in MIN_GRID_COLUMNS..MAX_GRID_COLUMNS) {
            "gridColumns must be between $MIN_GRID_COLUMNS and $MAX_GRID_COLUMNS"
        }
        require(theme.isNotBlank()) { "theme must not be blank" }
        require(updatedAt >= 0L) { "updatedAt must not be negative" }
    }

    fun validate() {
        require(gridColumns in MIN_GRID_COLUMNS..MAX_GRID_COLUMNS) {
            "gridColumns must be between $MIN_GRID_COLUMNS and $MAX_GRID_COLUMNS"
        }
        require(theme.isNotBlank()) { "theme must not be blank" }
        require(updatedAt >= 0L) { "updatedAt must not be negative" }
    }

    companion object {
        const val MIN_GRID_COLUMNS: Int = 2
        const val MAX_GRID_COLUMNS: Int = 4
        const val DEFAULT_GRID_COLUMNS: Int = 3
        /** Matches the app's default OLED theme identifier. */
        const val DEFAULT_THEME: String = "oled"
    }
}
