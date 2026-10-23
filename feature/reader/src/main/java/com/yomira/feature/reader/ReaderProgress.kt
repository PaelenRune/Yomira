package com.yomira.feature.reader

import androidx.room.withTransaction
import com.yomira.core.database.YomiraDatabase
import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.core.model.ReadingMode
import javax.inject.Inject

fun ReadingMode.isRightToLeft(): Boolean = this == ReadingMode.MANGA_RTL

fun boundedPage(index: Int, count: Int): Int = index.coerceIn(0, (count - 1).coerceAtLeast(0))

class ReaderProgressStore internal constructor(
    private val read: suspend (String) -> ReaderProgressEntity?,
    private val write: suspend (ReaderProgressEntity) -> Unit,
) {
    @Inject constructor(database: YomiraDatabase) : this(
        read = { database.chapterDao().getProgress(it) },
        write = { progress ->
            database.withTransaction {
                database.chapterDao().saveProgress(progress)
                database.syncDao().markReaderDirty("local-reader")
            }
        },
    )

    suspend fun restore(chapterId: String): ReaderProgressEntity? = read(chapterId)

    suspend fun save(progress: ReaderProgressEntity) {
        require(progress.pageCount > 0 && progress.pageIndex in 0 until progress.pageCount)
        require(progress.chapterIndex >= 0)
        write(progress)
    }
}
