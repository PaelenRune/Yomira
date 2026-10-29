package com.yomira.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesAliasMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), YomiraDatabase::class.java)

    @Test fun migrationAddsTheAliasDictionaryWithoutTouchingExistingRows() {
        val name = "series-alias-migration"
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO manga (id, title, sourceId) VALUES ('baka-updates:42', 'Attack on Titan', 'baka-updates')")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT title FROM manga WHERE id = 'baka-updates:42'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Attack on Titan", cursor.getString(0))
            }
            db.execSQL(
                "INSERT INTO series_aliases (seriesId, alias, normalizedAlias, lang, source) " +
                    "VALUES ('baka-updates:42', 'Shingeki no Kyojin', 'shingeki no kyojin', 'en-jp', 'baka-updates')")
            db.query("SELECT DISTINCT seriesId FROM series_aliases WHERE normalizedAlias = 'shingeki no kyojin'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("baka-updates:42", cursor.getString(0))
            }
        }
    }
}
