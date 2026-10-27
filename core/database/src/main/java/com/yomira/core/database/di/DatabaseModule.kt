package com.yomira.core.database.di

import android.content.Context
import androidx.room.Room
import com.yomira.core.database.YomiraDatabase
import com.yomira.core.database.MIGRATION_2_3
import com.yomira.core.database.MIGRATION_3_4
import com.yomira.core.database.MIGRATION_4_5
import com.yomira.core.database.MIGRATION_5_6
import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.dao.JourneyProfileDao
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.MetadataCacheDao
import com.yomira.core.database.dao.ReadChapterDao
import com.yomira.core.database.dao.ReadingActivityDao
import com.yomira.core.database.dao.SyncDao
import com.yomira.core.database.dao.SyncDataDao
import com.yomira.core.database.dao.UnlockedBadgeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YomiraDatabase =
        Room.databaseBuilder(context, YomiraDatabase::class.java, "yomira.db")
            .addMigrations(YomiraDatabase.MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            // A device that carries an older, partially migrated or otherwise incompatible
            // schema must still start: rebuild the cache-like tables instead of throwing
            // IllegalStateException out of Room during the first launch.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideMangaDao(database: YomiraDatabase): MangaDao = database.mangaDao()

    @Provides
    fun provideChapterDao(database: YomiraDatabase): ChapterDao = database.chapterDao()

    @Provides
    fun provideMetadataCacheDao(database: YomiraDatabase): MetadataCacheDao = database.metadataCacheDao()

    @Provides
    fun provideSyncDao(database: YomiraDatabase): SyncDao = database.syncDao()

    @Provides
    fun provideSyncDataDao(database: YomiraDatabase): SyncDataDao = database.syncDataDao()

    @Provides
    fun provideReadingActivityDao(database: YomiraDatabase): ReadingActivityDao = database.readingActivityDao()

    @Provides
    fun provideReadChapterDao(database: YomiraDatabase): ReadChapterDao = database.readChapterDao()

    @Provides
    fun provideJourneyProfileDao(database: YomiraDatabase): JourneyProfileDao = database.journeyProfileDao()

    @Provides
    fun provideUnlockedBadgeDao(database: YomiraDatabase): UnlockedBadgeDao = database.unlockedBadgeDao()
}
