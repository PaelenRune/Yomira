package com.yomira.domain.sync

import com.yomira.data.sync.InstalledExtensionSyncEntry
import com.yomira.data.sync.SyncPayload

/** Local library restore must not materialize pending extensions or change device preferences. */
internal suspend fun writeSyncProjection(
    payload: SyncPayload,
    libraryOnly: Boolean,
    writeDatabase: suspend (SyncPayload) -> Unit,
    writeExtensions: suspend (List<InstalledExtensionSyncEntry>) -> Unit,
) {
    writeDatabase(if (libraryOnly) payload.copy(settings = null, extensions = emptyList()) else payload)
    if (!libraryOnly) writeExtensions(payload.extensions)
}
