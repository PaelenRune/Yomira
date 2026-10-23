package com.yomira.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 adds the local projections of the cloud sync document. The new tables are
 * independent of the catalogue so remote user state can arrive before metadata does.
 * Reader progress (v2) stays the in-reader source of truth; these tables are the
 * cloud-facing projection that survives chapter index refreshes.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `library_entries` (" +
                "`mangaId` TEXT NOT NULL, " +
                "`inLibrary` INTEGER NOT NULL, " +
                "`category` TEXT, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mangaId`))",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_progress` (" +
                "`mangaId` TEXT NOT NULL, " +
                "`chapterNumber` REAL NOT NULL, " +
                "`pageIndex` INTEGER NOT NULL, " +
                "`lastReadAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mangaId`))",
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_settings` (" +
                "`id` INTEGER NOT NULL, " +
                "`gridColumns` INTEGER NOT NULL, " +
                "`adultEnabled` INTEGER NOT NULL, " +
                "`theme` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}
