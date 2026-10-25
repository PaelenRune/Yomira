package com.yomira.domain.journey

import com.yomira.core.database.dao.ReadChapterDao
import com.yomira.core.model.ReadingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Converts reader gestures into local, weighted activity.
 *
 * Responsibility split:
 *  - [ActiveReadingTimer] decides whether time was actually active (60s idle cap).
 *  - this tracker deduplicates pages, flushes cheap partial slices and hands the
 *    completion to [ReadChapterDao] so re-opened chapters cannot pay full XP twice.
 *  - [JourneyRepository] persists the aggregate and refreshes achievements.
 *
 * Reader callbacks are cheap and suspend on a mutex; the caller runs them from
 * its own coroutine scope, never on the main thread.
 */
@Singleton
class ReadingSessionTracker @Inject constructor(
    private val readChapterDao: ReadChapterDao,
    private val journeyRepository: JourneyRepository,
    private val clock: JourneyClock,
) {
    private data class Session(
        val mangaId: String,
        val chapterId: String,
        val seriesTitle: String,
        val genre: String,
        val mode: ReadingMode,
        val isWestern: Boolean,
        val chapterCountInSeries: Int,
        val seenPages: MutableSet<Int> = mutableSetOf(),
        var pagesSinceFlush: Int = 0,
        var completed: Boolean = false,
    )

    private val mutex = Mutex()
    private val timer = ActiveReadingTimer()
    private var session: Session? = null

    suspend fun startChapter(
        mangaId: String,
        chapterId: String,
        seriesTitle: String,
        genre: String,
        mode: ReadingMode,
        isWestern: Boolean,
        chapterCountInSeries: Int,
    ) = mutex.withLock {
        persistLocked(chapterCompleted = false)
        val now = clock.nowMillis()
        session = Session(
            mangaId = mangaId,
            chapterId = chapterId,
            seriesTitle = seriesTitle,
            genre = genre,
            mode = mode,
            isWestern = isWestern,
            chapterCountInSeries = chapterCountInSeries.coerceAtLeast(0),
        )
        timer.reset()
        timer.start(now)
    }

    suspend fun onInteraction(pageIndex: Int, pageCount: Int) = mutex.withLock {
        val current = session ?: return@withLock
        if (current.completed) return@withLock
        val now = clock.nowMillis()
        timer.interaction(now)
        if (pageIndex in 0 until pageCount.coerceAtLeast(0) && current.seenPages.add(pageIndex)) {
            current.pagesSinceFlush++
        }
        // Cheap, bounded writes: never one row per scroll tick.
        if (current.pagesSinceFlush >= PARTIAL_FLUSH_PAGES || timer.pendingMillis() >= PARTIAL_FLUSH_MILLIS) {
            persistLocked(chapterCompleted = false)
        }
    }

    /**
     * Called when the last page of a chapter becomes visible. A series counts as
     * completed only when the ledger proves every known chapter was finished, so
     * jumping to the final chapter cannot award the series-completion marker.
     */
    suspend fun completeChapter() = mutex.withLock {
        val current = session ?: return@withLock
        if (current.completed) return@withLock
        val now = clock.nowMillis()
        val firstRead = readChapterDao.recordCompletion(current.mangaId, current.chapterId, now)
        val finishedChapters = readChapterDao.completedCountForManga(current.mangaId)
        val seriesComplete = current.chapterCountInSeries > 0 &&
            finishedChapters >= current.chapterCountInSeries
        current.completed = true
        persistLocked(
            chapterCompleted = true,
            seriesCompleted = seriesComplete,
            firstRead = firstRead,
            xpPagesRead = current.seenPages.size,
        )
        session = null
        timer.reset()
        journeyRepository.refreshAchievements()
    }

    suspend fun flush() = mutex.withLock { persistLocked(chapterCompleted = false) }

    fun pause() = timer.pause()

    suspend fun end() = mutex.withLock {
        persistLocked(chapterCompleted = false)
        session = null
        timer.reset()
    }

    private suspend fun persistLocked(
        chapterCompleted: Boolean,
        seriesCompleted: Boolean = false,
        firstRead: Boolean = false,
        xpPagesRead: Int = 0,
    ) {
        val current = session ?: return
        val pages = current.pagesSinceFlush
        val active = timer.drainActiveMillis()
        if (!chapterCompleted && pages == 0 && active == 0L) return
        journeyRepository.recordActivity(
            mangaId = current.mangaId,
            seriesTitle = current.seriesTitle,
            genre = current.genre,
            readingMode = current.mode,
            pagesRead = pages,
            xpPagesRead = if (chapterCompleted) maxOf(xpPagesRead, current.seenPages.size) else 0,
            chapterCompleted = chapterCompleted,
            seriesCompleted = seriesCompleted,
            firstRead = firstRead,
            activeMillis = active,
            isWestern = current.isWestern,
        )
        current.pagesSinceFlush = 0
    }

    companion object {
        const val PARTIAL_FLUSH_PAGES = 8
        const val PARTIAL_FLUSH_MILLIS = 20_000L
    }
}
