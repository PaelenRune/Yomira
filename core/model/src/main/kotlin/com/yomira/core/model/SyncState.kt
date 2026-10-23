package com.yomira.core.model

import java.time.Instant

data class SyncState(
    val lastSyncedAt: Instant? = null,
    val deviceId: String,
    val version: Long = 0,
) {
    init {
        require(deviceId.isNotBlank()) { "Device ID must not be blank" }
        require(version >= 0) { "Sync version must not be negative" }
    }
}
