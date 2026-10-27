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
import com.yomira.data.sync.ReadingHistorySyncEntry
import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.core.database.entity.ReadChapterEntity
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
    private val extensions: SyncExtensionStore,
) {
    suspend fun read(): SyncPayload {
        val installed = extensions.read()
        return database.withTransaction {
            val deviceId = identity.deviceId()
            val state = syncDao.getByDeviceId(deviceId)
            val settings = syncDataDao.getSettings() ?: SyncSettingsEntity()
            val completions = syncDataDao.getReadChapters().associateBy { it.mangaId to it.chapterId }
            val positions = syncDataDao.getReaderProgress().map {
                ReadingHistorySyncEntry(
                    it.mangaId, it.chapterId, it.chapterIndex, it.pageIndex,
                    it.pageCount, it.updatedAt, it.pageCount > 0 && it.pageIndex == it.pageCount - 1,
                )
            }.associateBy { it.mangaId to it.chapterId }.toMutableMap()
            completions.forEach { (key, completion) ->
                val position = positions[key]
                if (position == null) {
                    positions[key] = ReadingHistorySyncEntry(
                        completion.mangaId, completion.chapterId,
                        lastReadAt = completion.lastReadAt, completed = true,
                    )
                } else if (completion.lastReadAt >= position.lastReadAt) {
                    positions[key] = position.copy(lastReadAt = completion.lastReadAt, completed = true)
                }
            }
            val resume = syncDataDao.getAllReadingProgress().map(ReadingProgressEntity::toPayload)
                .associateBy { it.mangaId }.toMutableMap()
            positions.values.forEach { position ->
                if (position.lastReadAt >= (resume[position.mangaId]?.lastReadAt ?: -1)) {
                    resume[position.mangaId] = ReadingProgressSyncEntry(
                        position.mangaId, position.chapterIndex.toDouble(), position.pageIndex,
                        position.lastReadAt, position.chapterId, position.completed,
                    )
                }
            }

            SyncPayload(
                deviceId = deviceId,
                version = state?.version ?: 0L,
                generatedAt = System.currentTimeMillis(),
                library = syncDataDao.getAllLibraryEntries().map(LibraryEntryEntity::toPayload),
                readingProgress = resume.values.toList(),
                settings = settings.toPayload(),
                readingHistory = positions.values.toList(),
                extensions = installed,
            )
        }
    }

    suspend fun write(payload: SyncPayload) {
        database.withTransaction {
            // A user may keep reading during the network call. Never overwrite a newer local edit.
            val currentLibrary = syncDataDao.getAllLibraryEntries().associateBy { it.mangaId }
            val library = payload.library.filter { it.updatedAt >= (currentLibrary[it.mangaId]?.updatedAt ?: -1) }
                .map(LibrarySyncEntry::toEntity)
            if (library.isNotEmpty()) syncDataDao.upsertLibraryEntries(library)

            val currentProgress = syncDataDao.getAllReadingProgress().associateBy { it.mangaId }
            val progress = payload.readingProgress.filter { it.lastReadAt >= (currentProgress[it.mangaId]?.lastReadAt ?: -1) }
                .map(ReadingProgressSyncEntry::toEntity)
            if (progress.isNotEmpty()) syncDataDao.upsertReadingProgress(progress)

            payload.readingHistory.forEach { entry ->
                val local = database.chapterDao().getProgress(entry.chapterId)
                if (entry.pageCount > 0 && entry.lastReadAt >= (local?.updatedAt ?: -1)) {
                    database.chapterDao().saveProgress(ReaderProgressEntity(
                        entry.chapterId, entry.mangaId, entry.chapterIndex,
                        entry.pageIndex, entry.pageCount, entry.lastReadAt,
                    ))
                }
                // Import the completion ledger without awarding XP a second time.
                if (entry.completed) {
                    val completed = database.readChapterDao().get(entry.mangaId, entry.chapterId)
                    if (completed == null) {
                        database.readChapterDao().insert(ReadChapterEntity(
                            entry.mangaId, entry.chapterId, entry.lastReadAt, entry.lastReadAt,
                            fullXpAwarded = true, completedAt = entry.lastReadAt,
                        ))
                    } else if (entry.lastReadAt > completed.lastReadAt) {
                        database.readChapterDao().upsert(
                            completed.copy(lastReadAt = entry.lastReadAt, completedAt = entry.lastReadAt),
                        )
                    }
                }
            }
            payload.settings?.let {
                if (it.updatedAt >= (syncDataDao.getSettings()?.updatedAt ?: -1)) {
                    syncDataDao.upsertSettings(it.toEntity())
                }
            }
        }
        extensions.write(payload.extensions)
    }
}

private fun LibraryEntryEntity.toPayload(): LibrarySyncEntry = LibrarySyncEntry(
    mangaId = mangaId,
    inLibrary = inLibrary,
    category = category,
    updatedAt = updatedAt,
    favorite = favorite,
    categories = categories,
    customTags = customTags,
)

private fun LibrarySyncEntry.toEntity(): LibraryEntryEntity = LibraryEntryEntity(
    mangaId = mangaId,
    inLibrary = inLibrary,
    category = category,
    updatedAt = updatedAt,
    favorite = favorite,
    categories = categories,
    customTags = customTags,
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
