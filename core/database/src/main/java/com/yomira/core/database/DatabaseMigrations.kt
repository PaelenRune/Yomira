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

/**
 * v4 adds the local-only Reading Journey. Nothing here is synced: the user's
 * reading rhythm, achievement ledger and badge unlocks stay on the device.
 *
 * The activity ledger stores aggregates only (pages, chapters, active millis,
 * weighted pages); it never carries page URLs or image bytes.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_activity` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`seriesId` TEXT NOT NULL, " +
                "`seriesTitle` TEXT NOT NULL, " +
                "`genre` TEXT NOT NULL, " +
                "`readingMode` TEXT NOT NULL, " +
                "`occurredAt` INTEGER NOT NULL, " +
                "`dayEpoch` INTEGER NOT NULL, " +
                "`hourOfDay` INTEGER NOT NULL, " +
                "`pageCount` INTEGER NOT NULL, " +
                "`chapterCount` INTEGER NOT NULL, " +
                "`activeMillis` INTEGER NOT NULL, " +
                "`weightedPages` REAL NOT NULL, " +
                "`xp` INTEGER NOT NULL, " +
                "`completedSeries` INTEGER NOT NULL, " +
                "`isWestern` INTEGER NOT NULL, " +
                "`isNight` INTEGER NOT NULL, " +
                "`isWeekend` INTEGER NOT NULL)",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_activity_dayEpoch` ON `reading_activity` (`dayEpoch`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_activity_occurredAt` ON `reading_activity` (`occurredAt`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_activity_seriesId` ON `reading_activity` (`seriesId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_activity_genre` ON `reading_activity` (`genre`)")

        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `read_chapters` (" +
                "`mangaId` TEXT NOT NULL, " +
                "`chapterId` TEXT NOT NULL, " +
                "`firstReadAt` INTEGER NOT NULL, " +
                "`lastReadAt` INTEGER NOT NULL, " +
                "`completionCount` INTEGER NOT NULL, " +
                "`fullXpAwarded` INTEGER NOT NULL, " +
                "`completedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mangaId`, `chapterId`))",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_read_chapters_mangaId` ON `read_chapters` (`mangaId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_read_chapters_completedAt` ON `read_chapters` (`completedAt`)")

        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `journey_profile` (" +
                "`id` INTEGER NOT NULL, " +
                "`bonusXp` INTEGER NOT NULL, " +
                "`lastActiveDayEpoch` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )

        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `unlocked_badges` (" +
                "`badgeId` TEXT NOT NULL, " +
                "`tier` TEXT NOT NULL, " +
                "`unlockedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`badgeId`, `tier`))",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_unlocked_badges_unlockedAt` ON `unlocked_badges` (`unlockedAt`)")
    }
}
