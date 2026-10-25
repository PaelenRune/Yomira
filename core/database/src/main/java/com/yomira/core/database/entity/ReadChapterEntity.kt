package com.yomira.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Idempotency ledger for chapter completions.
 *
 * A composite primary key means the same chapter opened on two devices (or
 * simply re-opened tomorrow) is still one first read. The journey layer uses
 * [completionCount] to award reduced XP on re-reads; it never awards the full
 * first-read amount twice.
 *
 * There is intentionally no foreign key to `manga`: progress and achievements
 * received from another device must survive until the catalogue row exists.
 */
@Entity(
    tableName = "read_chapters",
    primaryKeys = ["mangaId", "chapterId"],
    indices = [Index("mangaId"), Index("completedAt")],
)
data class ReadChapterEntity(
    val mangaId: String,
    val chapterId: String,
    val firstReadAt: Long,
    val lastReadAt: Long,
    val completionCount: Int = 1,
    /** True once the full first-read XP bundle has been paid for this chapter. */
    val fullXpAwarded: Boolean = true,
    val completedAt: Long,
)
