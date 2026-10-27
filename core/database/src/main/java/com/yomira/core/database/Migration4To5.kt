package com.yomira.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve user library state when introducing cross-device categories and tags. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_entries ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE library_entries ADD COLUMN categories TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE library_entries ADD COLUMN customTags TEXT NOT NULL DEFAULT '[]'")
    }
}
