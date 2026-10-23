package com.yomira.domain.sync

import androidx.room.withTransaction
import com.yomira.core.database.YomiraDatabase
import com.yomira.core.database.dao.SyncDao
import com.yomira.core.database.dao.SyncDataDao
import com.yomira.core.database.entity.LibraryEntryEntity
import com.yomira.core.database.entity.ReadingProgressEntity
import com.yomira.core.database.entity.SyncSettingsEntity
import com.yomira.data.sync.LibrarySyncEntry
import com.yomira.data.sync.ReadingProgressSyncEntry
import com.yomira.data.sync.SyncPayload
import com.yomira.data.sync.UserSettingsSync
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed local projection of the cloud document.
 *
 * Reads and writes happen in a single Room transaction so a background worker can
 * never observe a half-merged state. Writes are upserts: entries created on another
 * device are added, but local rows absent from the merged payload are not deleted.
 */
@Singleton
class SyncLocalStore @Inject constructor(
    private val database: YomiraDatabase,
    private val syncDataDao: SyncDataDao,
    private val syncDao: SyncDao,
    private val identity: SyncIdentityStore,
) {
    suspend fun read(): SyncPayload = database.withTransaction {
        val deviceId = identity.deviceId()
        val state = syncDao.getByDeviceId(deviceId)
        val settings = syncDataDao.getSettings() ?: SyncSettingsEntity()

        SyncPayload(
            deviceId = deviceId,
            version = state?.version ?: 0L,
            generatedAt = System.currentTimeMillis(),
            library = syncDataDao.getAllLibraryEntries().map(LibraryEntryEntity::toPayload),
            readingProgress = syncDataDao.getAllReadingProgress().map(ReadingProgressEntity::toPayload),
            settings = settings.toPayload(),
        )
    }

    suspend fun write(payload: SyncPayload) = database.withTransaction {
        val library = payload.library.map(LibrarySyncEntry::toEntity)
        if (library.isNotEmpty()) syncDataDao.upsertLibraryEntries(library)

        val progress = payload.readingProgress.map(ReadingProgressSyncEntry::toEntity)
        if (progress.isNotEmpty()) syncDataDao.upsertReadingProgress(progress)

        payload.settings?.let { syncDataDao.upsertSettings(it.toEntity()) }
    }
}

private fun LibraryEntryEntity.toPayload(): LibrarySyncEntry = LibrarySyncEntry(
    mangaId = mangaId,
    inLibrary = inLibrary,
    category = category,
    updatedAt = updatedAt,
)

private fun LibrarySyncEntry.toEntity(): LibraryEntryEntity = LibraryEntryEntity(
    mangaId = mangaId,
    inLibrary = inLibrary,
    category = category,
    updatedAt = updatedAt,
)

private fun ReadingProgressEntity.toPayload(): ReadingProgressSyncEntry = ReadingProgressSyncEntry(
    mangaId = mangaId,
    chapterNumber = chapterNumber,
    pageIndex = pageIndex,
    lastReadAt = lastReadAt,
)

private fun ReadingProgressSyncEntry.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
    mangaId = mangaId,
    chapterNumber = chapterNumber,
    pageIndex = pageIndex,
    lastReadAt = lastReadAt,
)

private fun SyncSettingsEntity.toPayload(): UserSettingsSync = UserSettingsSync(
    gridColumns = gridColumns,
    adultEnabled = adultEnabled,
    theme = theme,
    updatedAt = updatedAt,
)

private fun UserSettingsSync.toEntity(): SyncSettingsEntity = SyncSettingsEntity(
    id = SyncSettingsEntity.SINGLETON_ID,
    gridColumns = gridColumns,
    adultEnabled = adultEnabled,
    theme = theme,
    updatedAt = updatedAt,
)
