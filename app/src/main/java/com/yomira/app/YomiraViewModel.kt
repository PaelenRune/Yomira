package com.yomira.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.SyncDao
import com.yomira.core.database.mapper.toModel
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.domain.extension.*
import com.yomira.feature.details.ChapterUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.Optional
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogState(val manga: List<Manga> = emptyList(), val updated: List<Manga> = emptyList(), val loading: Boolean = true, val error: String? = null)

@HiltViewModel
class YomiraViewModel @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val syncDao: SyncDao,
    private val matcher: Optional<SmartSourceMatcher>,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val preferences = context.getSharedPreferences("yomira_ui", Context.MODE_PRIVATE)
    val catalog = MutableStateFlow(CatalogState())
    val columns = MutableStateFlow(preferences.getInt("columns", 3).coerceIn(2, 4))
    val vaultColumns = MutableStateFlow(preferences.getInt("vault_columns", 3).coerceIn(2, 4))
    val saved = MutableStateFlow(preferences.getStringSet("saved", emptySet())!!.toSet())
    private val read = MutableStateFlow(preferences.getStringSet("read", emptySet())!!.toSet())
    val unlocked = MutableStateFlow(false)
    val match = MutableStateFlow<SmartMatchResult?>(null)
    val matching = MutableStateFlow(false)
    private var matchJob: Job? = null
    private var catalogJob: Job? = null

    init { refresh() }

    fun refresh() {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            catalog.value = catalog.value.copy(loading = true, error = null)
            combine(mangaDao.observeAll(), mangaDao.observeRecentlyUpdated()) { all, updated ->
                CatalogState(all.map { it.toModel() }, updated.map { it.toModel() }, loading = false)
            }.catch { error -> emit(CatalogState(loading = false, error = error.message ?: "Katalog yüklenemedi")) }.collect { catalog.value = it }
        }
    }

    fun setColumns(value: Int, vault: Boolean = false) {
        require(value in 2..4)
        (if (vault) vaultColumns else columns).value = value
        persist { putInt(if (vault) "vault_columns" else "columns", value) }
    }

    fun toggleSaved(id: String) {
        saved.value = if (id in saved.value) saved.value - id else saved.value + id
        val snapshot = saved.value
        persist { putStringSet("saved", snapshot) }
    }

    fun toggleRead(id: String) {
        read.value = if (id in read.value) read.value - id else read.value + id
        val snapshot = read.value
        persist { putStringSet("read", snapshot) }
    }

    private fun persist(edit: android.content.SharedPreferences.Editor.() -> Unit) {
        // apply() updates memory synchronously and serializes disk writes off the UI thread.
        // Keeping edits on the caller thread preserves rapid tap ordering.
        preferences.edit().apply(edit).apply()
    }

    fun chapters(id: String): Flow<List<ChapterUiState>> = combine(chapterDao.observeByMangaId(id), read) { chapters, readIds ->
        chapters.map { ChapterUiState(it.toModel(), it.id in readIds) }
    }

    fun resumeChapter(mangaId: String, fallbackId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val chapterId = try {
                val previous = syncDao.latestReaderProgress(mangaId)
                previous?.chapterId?.takeIf { chapterDao.getById(it)?.mangaId == mangaId } ?: fallbackId
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { fallbackId }
            onReady(chapterId)
        }
    }

    fun clearMatch() { matchJob?.cancel(); match.value = null; matching.value = false }

    fun lock() { unlocked.value = false; clearMatch() }

    fun findSource(manga: Manga) {
        if (manga.contentRating == ContentRating.EXPLICIT && !unlocked.value) return
        clearMatch()
        matchJob = viewModelScope.launch {
            matching.value = true
            try {
                match.value = if (matcher.isPresent) withContext(Dispatchers.IO) {
                    matcher.get().match(SeriesIdentity(manga.id, manga.title, manga.altTitles.toSet()), ReaderPreferences(ReadingLanguage.TURKISH, manga.contentRating == ContentRating.EXPLICIT && unlocked.value))
                } else SmartMatchResult.Unavailable(listOf(SourceFailure("runtime", "Güvenilir eklenti çalışma ortamı henüz bağlanmadı.")))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                match.value = SmartMatchResult.Unavailable(listOf(SourceFailure("runtime", error.message ?: "Kaynağa erişilemedi")))
            } finally { matching.value = false }
        }
    }
}
