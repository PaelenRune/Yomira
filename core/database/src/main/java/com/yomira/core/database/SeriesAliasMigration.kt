package com.yomira.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the series alias dictionary (v6 → v7). Pure addition: no existing
 * column moves, so the library, reading progress and metadata cache survive
 * untouched. Rows are filled lazily by the waterfall aggregator's alias sink.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `series_aliases` (" +
                "`seriesId` TEXT NOT NULL, `alias` TEXT NOT NULL, `normalizedAlias` TEXT NOT NULL, " +
                "`lang` TEXT, `source` TEXT NOT NULL, PRIMARY KEY(`seriesId`, `normalizedAlias`))",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_series_aliases_seriesId` ON `series_aliases` (`seriesId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_series_aliases_normalizedAlias` ON `series_aliases` (`normalizedAlias`)")
    }
}
