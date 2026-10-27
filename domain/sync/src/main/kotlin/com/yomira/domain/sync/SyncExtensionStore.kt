package com.yomira.domain.sync

import com.yomira.data.sync.InstalledExtensionSyncEntry

/** Android adapter owns manifests/configuration and verifies binaries before restoring them. */
interface SyncExtensionStore {
    suspend fun read(): List<InstalledExtensionSyncEntry>
    suspend fun write(entries: List<InstalledExtensionSyncEntry>)
}

/** Local consent must never be changed by the remote document. */
fun interface AutomaticSyncPreference {
    suspend fun enabled(): Boolean
}
