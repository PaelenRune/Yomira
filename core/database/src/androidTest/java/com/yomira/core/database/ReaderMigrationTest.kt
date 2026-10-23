package com.yomira.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), YomiraDatabase::class.java)

    @Test
    fun migrationPreservesExistingSyncAndCreatesProgressTable() {
        val name = "reader-migration-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO sync_state (deviceId, lastSyncedAt, version) VALUES ('device', 100, 9)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, YomiraDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT version FROM sync_state WHERE deviceId = 'device'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(9L, cursor.getLong(0))
            }
            db.execSQL("INSERT INTO reader_progress (chapterId, mangaId, chapterIndex, pageIndex, pageCount, updatedAt) VALUES ('c', 'm', 6, 13, 45, 200)")
            db.query("SELECT pageIndex FROM reader_progress WHERE chapterId = 'c'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(13, cursor.getInt(0))
            }
        }
    }
}
