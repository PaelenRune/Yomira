package com.yomira.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v4 is the local-only Reading Journey. The migration must add its four tables
 * and leave every pre-existing reading position untouched.
 */
@RunWith(AndroidJUnit4::class)
class JourneyMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), YomiraDatabase::class.java)

    @Test
    fun migrationAddsJourneyTablesAndKeepsProgress() {
        val name = "journey-migration-test"
        helper.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO reading_progress (mangaId, chapterNumber, pageIndex, lastReadAt) " +
                    "VALUES ('m1', 12.0, 7, 100)",
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT pageIndex FROM reading_progress WHERE mangaId = 'm1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(7, cursor.getInt(0))
            }

            db.execSQL(
                "INSERT INTO reading_activity (seriesId, seriesTitle, genre, readingMode, occurredAt, dayEpoch, " +
                    "hourOfDay, pageCount, chapterCount, activeMillis, weightedPages, xp, completedSeries, " +
                    "isWestern, isNight, isWeekend) VALUES " +
                    "('m1', 'One Piece', 'Action', 'MANGA_RTL', 1000, 20000, 1, 10, 1, 45000, 10.0, 20, 0, 0, 1, 0)",
            )
            db.query("SELECT chapterCount FROM reading_activity WHERE seriesId = 'm1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

            db.execSQL(
                "INSERT INTO read_chapters (mangaId, chapterId, firstReadAt, lastReadAt, completionCount, " +
                    "fullXpAwarded, completedAt) VALUES ('m1', 'c1', 1000, 1000, 1, 1, 1000)",
            )
            db.query("SELECT COUNT(*) FROM read_chapters").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

            db.execSQL(
                "INSERT INTO journey_profile (id, bonusXp, lastActiveDayEpoch, updatedAt) VALUES (1, 50, 20000, 1000)",
            )
            db.execSQL(
                "INSERT INTO unlocked_badges (badgeId, tier, unlockedAt) VALUES ('first_step', 'BRONZE', 1000)",
            )
            db.query("SELECT bonusXp FROM journey_profile WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals(50, cursor.getInt(0))
            }
        }
    }
}
