package com.yomira.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.entity.ReaderProgressEntity
import com.yomira.core.model.ReadingMode
import com.yomira.domain.extension.SourcePage
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
    val mode: ReadingMode = ReadingMode.MANGA_RTL,
    val oled: Boolean = true, val inverted: Boolean = false, val hudVisible: Boolean = true,
    val loading: Boolean = true, val error: String? = null, val progressError: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val repository: ReaderPageRepository,
    private val progress: ReaderProgressStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private val writes = Channel<ReaderProgressEntity>(Channel.UNLIMITED)

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

    fun open(mangaId: String, chapterId: String) {
        loadJob?.cancel()
        mutableState.value = ReaderUiState(mangaId = mangaId, chapterId = chapterId)
        loadJob = viewModelScope.launch {
            try {
                val manga = checkNotNull(mangaDao.getById(mangaId)) { "Seri bulunamadı." }
                val chapter = checkNotNull(chapterDao.getById(chapterId)) { "Bölüm bulunamadı." }
                require(chapter.mangaId == mangaId) { "Bölüm bu seriye ait değil." }
                val chapters = chapterDao.getByMangaId(mangaId)
                val saved = progress.restore(chapterId)
                val pages = repository.pages(manga.sourceId, chapter)
                mutableState.update { it.copy(title = manga.title, chapterTitle = chapter.title ?: "Bölüm ${chapter.chapterNumber}",
                    chapterIndex = chapters.indexOfFirst { item -> item.id == chapterId }.coerceAtLeast(0),
                    pages = pages, pageIndex = boundedPage(saved?.pageIndex ?: 0, pages.size), mode = manga.readingMode, loading = false) }
                recordPage(state.value.pageIndex)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = error.message ?: "Bölüm yüklenemedi.") }
            }
        }
    }

    fun recordPage(index: Int) {
        val snapshot = state.value
        if (snapshot.pages.isEmpty()) return
        val page = boundedPage(index, snapshot.pages.size)
        mutableState.update { it.copy(pageIndex = page) }
        writes.trySend(ReaderProgressEntity(snapshot.chapterId, snapshot.mangaId, snapshot.chapterIndex, page, snapshot.pages.size, System.currentTimeMillis()))
    }

    fun close(mangaId: String, chapterId: String) {
        if (state.value.mangaId == mangaId && state.value.chapterId == chapterId) {
            loadJob?.cancel()
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
    fun retryProgress() = recordPage(state.value.pageIndex)
}
