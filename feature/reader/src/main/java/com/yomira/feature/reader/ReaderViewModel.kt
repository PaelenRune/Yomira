package com.yomira.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.core.database.mapper.toModel
import com.yomira.core.model.ReadingMode
import com.yomira.core.model.WesternComics
import com.yomira.domain.extension.SourcePage
import com.yomira.domain.journey.ReadingSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val mangaId: String = "", val chapterId: String = "",
    val title: String = "", val chapterTitle: String = "", val chapterIndex: Int = 0,
    val pages: List<SourcePage> = emptyList(), val pageIndex: Int = 0,
    val isLastChapter: Boolean = false,
    val mode: ReadingMode = ReadingMode.MANGA_RTL,
    val oled: Boolean = true, val inverted: Boolean = false, val hudVisible: Boolean = true,
    /** Guided View: one-tap, panel-by-panel cinematic reading. */
    val guidedView: Boolean = false,
    /** "What happened so far", capped at the chapter the user has actually reached. */
    val recap: StoryRecap? = null,
    /** True when the break since the last session is long enough to offer the recap. */
    val recapDue: Boolean = false,
    /** True while the recap sheet is on screen. */
    val recapVisible: Boolean = false,
    val loading: Boolean = true, val error: String? = null, val progressError: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val repository: ReaderPageRepository,
    private val progress: ReaderProgressStore,
    private val journey: ReadingSessionTracker,
) : ViewModel() {
    private companion object {
        /** One week away from a series is enough to forget where the story stood. */
        const val LONG_BREAK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    private val mutableState = MutableStateFlow(ReaderUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private val writes = Channel<ReaderProgressEntity>(Channel.UNLIMITED)
    private val journeyEvents = Channel<JourneyEvent>(Channel.UNLIMITED)

    private sealed interface JourneyEvent {
        data class Start(
            val mangaId: String,
            val chapterId: String,
            val title: String,
            val genre: String,
            val mode: ReadingMode,
            val isWestern: Boolean,
            val chapterCount: Int,
        ) : JourneyEvent
        data class Page(val index: Int, val count: Int) : JourneyEvent
        data object Complete : JourneyEvent
        data object Pause : JourneyEvent
        data object End : JourneyEvent
    }

    init {
        viewModelScope.launch {
            try {
                for (entry in writes) persist(entry)
            } finally {
                // Navigation can clear the ViewModel immediately after the last page callback.
                withContext(NonCancellable) {
                    while (true) persist(writes.tryReceive().getOrNull() ?: break)
                }
            }
        }
        viewModelScope.launch {
            try {
                for (event in journeyEvents) handleJourney(event)
            } finally {
                withContext(NonCancellable) {
                    while (true) handleJourney(journeyEvents.tryReceive().getOrNull() ?: break)
                    journey.end()
                }
            }
        }
    }

    private suspend fun handleJourney(event: JourneyEvent) {
        try {
            when (event) {
                is JourneyEvent.Start -> journey.startChapter(
                    mangaId = event.mangaId,
                    chapterId = event.chapterId,
                    seriesTitle = event.title,
                    genre = event.genre,
                    mode = event.mode,
                    isWestern = event.isWestern,
                    chapterCountInSeries = event.chapterCount,
                )
                is JourneyEvent.Page -> journey.onInteraction(event.index, event.count)
                JourneyEvent.Complete -> journey.completeChapter()
                JourneyEvent.Pause -> { journey.flush(); journey.pause() }
                JourneyEvent.End -> journey.end()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Reading must never fail because the gamification ledger is unavailable.
        }
    }

    private suspend fun persist(entry: ReaderProgressEntity) {
        try {
            progress.save(entry)
            mutableState.update { it.copy(progressError = null) }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { progress.save(entry) }
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(progressError = "Okuma konumu kaydedilemedi. Yeniden dene.") }
        }
    }

    fun open(
        mangaId: String,
        chapterId: String,
        defaultMode: ReadingMode = ReadingMode.MANGA_RTL,
        defaultGuidedView: Boolean = false,
    ) {
        loadJob?.cancel()
        mutableState.value = ReaderUiState(
            mangaId = mangaId,
            chapterId = chapterId,
            mode = defaultMode,
            guidedView = defaultGuidedView,
        )
        loadJob = viewModelScope.launch {
            try {
                val manga = checkNotNull(mangaDao.getById(mangaId)) { "Seri bulunamadı." }
                val chapter = checkNotNull(chapterDao.getById(chapterId)) { "Bölüm bulunamadı." }
                require(chapter.mangaId == mangaId) { "Bölüm bu seriye ait değil." }
                val chapters = chapterDao.getByMangaId(mangaId)
                val saved = progress.restore(chapterId)
                val pages = repository.pages(manga.sourceId, chapter)
                val isLastChapter = chapters.lastOrNull()?.id == chapterId
                mutableState.update { it.copy(title = manga.title, chapterTitle = chapter.title ?: "Bölüm ${chapter.chapterNumber}",
                    chapterIndex = chapters.indexOfFirst { item -> item.id == chapterId }.coerceAtLeast(0),
                    pages = pages, pageIndex = boundedPage(saved?.pageIndex ?: 0, pages.size), mode = manga.readingMode,
                    isLastChapter = isLastChapter, loading = false,
                    recap = buildRecap(chapters, chapterId),
                    recapDue = shouldOfferRecap(saved?.updatedAt ?: 0L)) }
                val genre = manga.genres.firstOrNull {
                    !it.equals(WesternComics.MARKER, ignoreCase = true)
                } ?: "Genel"
                journeyEvents.trySend(
                    JourneyEvent.Start(
                        mangaId = mangaId,
                        chapterId = chapterId,
                        title = manga.title,
                        genre = genre,
                        mode = manga.readingMode,
                        isWestern = WesternComics.matches(manga.toModel()),
                        chapterCount = chapters.size,
                    ),
                )
                recordPage(state.value.pageIndex, allowCompletion = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = error.message ?: "Bölüm yüklenemedi.") }
            }
        }
    }

    fun recordPage(index: Int) = recordPage(index, allowCompletion = true)

    private fun recordPage(index: Int, allowCompletion: Boolean) {
        val snapshot = state.value
        if (snapshot.pages.isEmpty()) return
        val page = boundedPage(index, snapshot.pages.size)
        mutableState.update { it.copy(pageIndex = page) }
        writes.trySend(ReaderProgressEntity(snapshot.chapterId, snapshot.mangaId, snapshot.chapterIndex, page, snapshot.pages.size, System.currentTimeMillis()))
        journeyEvents.trySend(JourneyEvent.Page(page, snapshot.pages.size))
        // The journey ledger deduplicates this callback; repeated last-page frames
        // cannot award the chapter twice.
        if (allowCompletion && page == snapshot.pages.lastIndex) {
            journeyEvents.trySend(JourneyEvent.Complete)
        }
    }

    /** Stops the active-time clock when the reader leaves the foreground. */
    fun pauseTracking() {
        journeyEvents.trySend(JourneyEvent.Pause)
    }

    /**
     * "Geçmiş Olayları Hatırla" recap for the series being opened.
     *
     * The spoiler horizon is the chapter *before* the one on screen: the recap
     * never summarises the chapter the user is about to continue, and it never
     * touches the chapters after it. Beats come from chapter titles, the only
     * per-chapter notes the catalogue stores on-device - no network call and no
     * generated text, so nothing can leak a future chapter.
     */
    private fun buildRecap(
        chapters: List<com.yomira.core.database.entity.ChapterEntity>,
        chapterId: String,
    ): StoryRecap? {
        val index = chapters.indexOfFirst { it.id == chapterId }
        val horizon = index - 1
        if (horizon < 0) return null
        // Only chapters at or before the horizon are handed over: the engine
        // rejects a source that already carries events from unread chapters.
        val covered = chapters.take(horizon + 1).mapIndexed { position, chapter ->
            val note = chapter.title?.takeIf { it.isNotBlank() }
            RecapChapter(
                index = position,
                chapterNumber = chapter.chapterNumber,
                title = note,
                events = listOfNotNull(note),
            )
        }
        if (covered.isEmpty()) return null
        return SpoilerFreeRecapEngine.build(covered, lastReadChapterIndex = horizon)
    }

    /** A long break since the last saved page makes "what happened so far" useful. */
    private fun shouldOfferRecap(lastReadAt: Long): Boolean {
        if (lastReadAt <= 0L) return false
        return System.currentTimeMillis() - lastReadAt >= LONG_BREAK_MILLIS
    }

    fun close(mangaId: String, chapterId: String) {
        if (state.value.mangaId == mangaId && state.value.chapterId == chapterId) {
            loadJob?.cancel()
            journeyEvents.trySend(JourneyEvent.End)
            mutableState.value = ReaderUiState()
        }
    }

    fun setMode(mode: ReadingMode) {
        val mangaId = state.value.mangaId
        mutableState.update { it.copy(mode = mode) }
        viewModelScope.launch {
            try { mangaDao.updateReadingMode(mangaId, mode)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.update { it.copy(progressError = "Okuma modu kaydedilemedi. Modu yeniden seç.") } }
        }
    }
    fun toggleHud() { mutableState.update { it.copy(hudVisible = !it.hudVisible) } }
    fun toggleOled() { mutableState.update { it.copy(oled = !it.oled) } }
    fun toggleInvert() { mutableState.update { it.copy(inverted = !it.inverted) } }

    /** One-tap Guided View switch; remembered for the open chapter. */
    fun toggleGuidedView() { mutableState.update { it.copy(guidedView = !it.guidedView) } }
    fun showRecap() { mutableState.update { it.copy(recapVisible = it.recap != null) } }
    fun dismissRecap() { mutableState.update { it.copy(recapVisible = false) } }

    fun retryProgress() = recordPage(state.value.pageIndex, allowCompletion = false)
}
