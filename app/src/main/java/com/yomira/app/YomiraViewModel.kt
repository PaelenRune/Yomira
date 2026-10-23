package com.yomira.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.app.catalog.CatalogSeeder
import com.yomira.app.catalog.TrendingCatalogLoader
import com.yomira.app.update.AppUpdateChecker
import com.yomira.app.update.UpdateInfo
import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.SyncDao
import com.yomira.core.database.mapper.toEntity
import com.yomira.core.database.mapper.toModel
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.data.metadata.WaterfallMetadataAggregator
import com.yomira.domain.extension.*
import com.yomira.feature.details.ChapterUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.Optional
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogState(val manga: List<Manga> = emptyList(), val updated: List<Manga> = emptyList(), val loading: Boolean = true, val error: String? = null)

/** Status of the on-demand waterfall lookup for a query that is not in Room yet. */
data class LiveSearchState(
    val query: String = "",
    val searching: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class YomiraViewModel @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val syncDao: SyncDao,
    private val matcher: Optional<SmartSourceMatcher>,
    private val onboardingStore: OnboardingStore,
    private val catalogSeeder: CatalogSeeder,
    private val trendingCatalogLoader: TrendingCatalogLoader,
    private val metadataAggregator: WaterfallMetadataAggregator,
    private val updateChecker: AppUpdateChecker,
    @ApplicationContext private val context: Context,
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
    val liveSearch = MutableStateFlow(LiveSearchState())
    val update = MutableStateFlow<UpdateInfo?>(null)
    private var matchJob: Job? = null
    private var catalogJob: Job? = null
    private var searchJob: Job? = null

    init {
        refresh()
        bootstrapCatalog()
        checkForUpdates()
    }

    fun refresh() {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            catalog.value = catalog.value.copy(loading = true, error = null)
            combine(mangaDao.observeAll(), mangaDao.observeRecentlyUpdated()) { all, updated ->
                CatalogState(all.map { it.toModel() }, updated.map { it.toModel() }, loading = false)
            }.catch { error -> emit(CatalogState(loading = false, error = error.message ?: "Katalog yüklenemedi")) }.collect { catalog.value = it }
        }
    }

    /**
     * First launch: Room is filled from the bundled catalogue, then (only when
     * the network happens to be reachable) trending titles are appended.
     * Both steps are best-effort; failure must leave the seeded grid intact.
     */
    private fun bootstrapCatalog() {
        viewModelScope.launch {
            val seeded = catalogSeeder.seedIfEmpty()
            if (seeded.inserted > 0) refresh()
            if (!hasInternet()) return@launch
            try {
                trendingCatalogLoader.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Trending is a bonus; the seeded catalogue is already on screen.
            }
        }
    }

    /** Trending discovery is optional; the curated seed must work offline. */
    private fun hasInternet(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Debounced lookup for a series that is not in the local database yet. */
    fun onQueryChanged(query: String) {
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.length < MIN_LIVE_QUERY_LENGTH) {
            liveSearch.value = LiveSearchState()
            return
        }
        if (isLocallyKnown(trimmed)) {
            liveSearch.value = LiveSearchState(query = trimmed)
            return
        }
        liveSearch.value = LiveSearchState(query = trimmed, searching = true)
        searchJob = viewModelScope.launch {
            delay(LIVE_SEARCH_DEBOUNCE_MS)
            try {
                val found = withContext(Dispatchers.IO) { metadataAggregator.aggregate(trimmed) }
                if (found == null) {
                    liveSearch.value = LiveSearchState(trimmed, message = "Çevrimiçi kaynaklarda sonuç bulunamadı.")
                    return@launch
                }
                if (found.contentRating == ContentRating.EXPLICIT && !unlocked.value) {
                    liveSearch.value = LiveSearchState(trimmed, message = "Bu seri yalnızca +18 Vault içinde gösterilir.")
                    return@launch
                }
                if (!mangaDao.existsByTitle(found.title)) {
                    mangaDao.upsert(found.toEntity(CatalogSeeder.readingModeFor(found.genres)))
                }
                liveSearch.value = LiveSearchState(trimmed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                liveSearch.value = LiveSearchState(trimmed, message = "Canlı arama şu anda yanıt vermiyor.")
            }
        }
    }

    private fun isLocallyKnown(query: String): Boolean = catalog.value.manga.any { manga ->
        manga.title.contains(query, ignoreCase = true) ||
            manga.altTitles.any { it.contains(query, ignoreCase = true) } ||
            manga.genres.any { it.contains(query, ignoreCase = true) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val info = try {
                updateChecker.check()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (preferences.getString(KEY_DISMISSED_UPDATE, null) == info.version) return@launch
            update.value = info
        }
    }

    /** Remembers the skipped version across restarts; a newer release still notifies. */
    fun dismissUpdate() {
        update.value?.let { persist { putString(KEY_DISMISSED_UPDATE, it.version) } }
        update.value = null
    }

    /** Closes the dialog after the download page was handed to the browser. */
    fun closeUpdate() {
        update.value = null
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
                    val settings = onboardingStore.read()?.preferences ?: ReaderPreferences(ReadingLanguage.TURKISH)
                    matcher.get().match(SeriesIdentity(manga.id, manga.title, manga.altTitles.toSet()),
                        settings.copy(allowAdultContent = settings.allowAdultContent && manga.contentRating == ContentRating.EXPLICIT && unlocked.value))
                } else SmartMatchResult.Unavailable(listOf(SourceFailure("runtime", "Güvenilir eklenti çalışma ortamı henüz bağlanmadı.")))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                match.value = SmartMatchResult.Unavailable(listOf(SourceFailure("runtime", error.message ?: "Kaynağa erişilemedi")))
            } finally { matching.value = false }
        }
    }

    private companion object {
        const val MIN_LIVE_QUERY_LENGTH = 3
        const val LIVE_SEARCH_DEBOUNCE_MS = 650L
        const val KEY_DISMISSED_UPDATE = "dismissed_update_version"
    }
}
