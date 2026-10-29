package com.yomira.app.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.yomira.core.database.YomiraDatabase
import com.yomira.core.database.dao.MangaDao
import com.yomira.data.extension.TachiyomiBackupImporter
import com.yomira.data.extension.TachiyomiBackupParser
import com.yomira.domain.sync.SyncLocalStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: YomiraDatabase,
    private val manga: MangaDao,
    private val local: SyncLocalStore,
    private val parser: TachiyomiBackupParser,
    private val importer: TachiyomiBackupImporter,
    private val target: RoomBackupImportTarget,
) {
    suspend fun create(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val backup = database.withTransaction {
            val snapshot = local.read()
            val library = snapshot.library.filter { it.inLibrary }
            val ids = library.map { it.mangaId }.toSet()
            val metadata = ids.map { BackupMetadata.from(checkNotNull(manga.getById(it))) }
            LocalBackup(metadata = metadata, state = snapshot.copy(library = library,
                readingProgress = snapshot.readingProgress.filter { it.mangaId in ids },
                readingHistory = snapshot.readingHistory.filter { it.mangaId in ids },
                settings = null, extensions = emptyList()))
        }
        val bytes = LocalBackupCodec.encode(backup)
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
    }

    suspend fun restore(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use {
            LocalBackupCodec.readBounded(it)
        }
        require(bytes.size <= LocalBackupCodec.MAX_BYTES)
        // Apply the existing parser's compression and format checks before touching Room.
        val document = parser.parse(bytes)
        val native = LocalBackupCodec.decodeNative(bytes)
        if (native == null) {
            require(document.mangas.isNotEmpty()) { "No library in backup" }
            importer.import(document, target)
        } else database.withTransaction {
            native.metadata.forEach { row ->
                if (manga.getById(row.id) == null) manga.upsert(row.toEntity())
            }
            // Timestamp-aware merge; never replace local security preferences or install extensions.
            local.restoreLibrary(native.state)
        }
    }
}
