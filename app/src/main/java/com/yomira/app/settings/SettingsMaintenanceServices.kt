package com.yomira.app.settings

import com.yomira.core.database.dao.SyncDao
import com.yomira.domain.sync.SyncIdentityStore
import com.yomira.domain.sync.SyncManager
import com.yomira.domain.sync.SyncResult
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Testable boundary around cache owners and the authenticated Drive transport. */
interface SettingsMaintenanceServices {
    val lastSyncedAt: Flow<Long?>
    suspend fun cacheSize(): Long
    suspend fun clearCache()
    suspend fun sync(): SyncResult
}

class DefaultSettingsMaintenanceServices @Inject constructor(
    private val cache: CacheManager,
    private val syncManager: SyncManager,
    private val syncDao: SyncDao,
    private val identity: SyncIdentityStore,
) : SettingsMaintenanceServices {
    override val lastSyncedAt: Flow<Long?> = flow {
        val deviceId = withContext(Dispatchers.IO) { identity.deviceId() }
        emitAll(syncDao.observeByDeviceId(deviceId).map { it?.lastSyncedAt?.time })
    }

    override suspend fun cacheSize(): Long = cache.getCacheSizeBytes()
    override suspend fun clearCache() { cache.clearAllCache().getOrThrow() }
    override suspend fun sync(): SyncResult = syncManager.sync()
}
