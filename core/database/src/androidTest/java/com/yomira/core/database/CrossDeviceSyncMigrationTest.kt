package com.yomira.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrossDeviceSyncMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), YomiraDatabase::class.java)

    @Test fun migrationPreservesExistingLibraryEntries() {
        val name = "cross-device-sync-migration"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO library_entries (mangaId, inLibrary, category, updatedAt) VALUES ('m', 1, 'Reading', 123)")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT * FROM library_entries WHERE mangaId = 'm'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Reading", cursor.getString(cursor.getColumnIndexOrThrow("category")))
                assertEquals(123L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("favorite")))
                assertEquals("[]", cursor.getString(cursor.getColumnIndexOrThrow("customTags")))
            }
        }
    }
}
