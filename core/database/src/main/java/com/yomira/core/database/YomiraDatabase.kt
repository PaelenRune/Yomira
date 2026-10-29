package com.yomira.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yomira.core.database.converter.DatabaseConverters
import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.JourneyProfileDao
import com.yomira.core.database.dao.MetadataCacheDao
import com.yomira.core.database.dao.ReadChapterDao
import com.yomira.core.database.dao.ReadingActivityDao
import com.yomira.core.database.dao.SeriesAliasDao
import com.yomira.core.database.dao.SyncDao
import com.yomira.core.database.dao.SyncDataDao
import com.yomira.core.database.dao.UnlockedBadgeDao
import com.yomira.core.database.entity.ChapterEntity
import com.yomira.core.database.entity.JourneyProfileEntity
import com.yomira.core.database.entity.LibraryEntryEntity
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.database.entity.MetadataCacheEntity
import com.yomira.core.database.entity.ReadChapterEntity
import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.core.database.entity.ReadingActivityEntity
import com.yomira.core.database.entity.ReadingProgressEntity
import com.yomira.core.database.entity.SeriesAliasEntity
import com.yomira.core.database.entity.SyncSettingsEntity
import com.yomira.core.database.entity.SyncStateEntity
import com.yomira.core.database.entity.UnlockedBadgeEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        MetadataCacheEntity::class,
        SyncStateEntity::class,
        ReaderProgressEntity::class,
        LibraryEntryEntity::class,
        ReadingProgressEntity::class,
        SyncSettingsEntity::class,
        ReadingActivityEntity::class,
        ReadChapterEntity::class,
        JourneyProfileEntity::class,
        UnlockedBadgeEntity::class,
        SeriesAliasEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class YomiraDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reader_progress (chapterId TEXT NOT NULL PRIMARY KEY, mangaId TEXT NOT NULL, chapterIndex INTEGER NOT NULL, pageIndex INTEGER NOT NULL, pageCount INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reader_progress_mangaId ON reader_progress (mangaId)")
            }
        }
    }

    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun metadataCacheDao(): MetadataCacheDao
    abstract fun syncDao(): SyncDao
    abstract fun syncDataDao(): SyncDataDao
    abstract fun readingActivityDao(): ReadingActivityDao
    abstract fun readChapterDao(): ReadChapterDao
    abstract fun journeyProfileDao(): JourneyProfileDao
    abstract fun unlockedBadgeDao(): UnlockedBadgeDao
    abstract fun seriesAliasDao(): SeriesAliasDao
}
