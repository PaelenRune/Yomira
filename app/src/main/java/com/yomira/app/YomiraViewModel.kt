package com.yomira.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.app.catalog.CatalogMerger
import com.yomira.app.catalog.CatalogSeeder
import com.yomira.app.catalog.PagedCatalogLoader
import com.yomira.app.catalog.TrendingCatalogLoader
import com.yomira.app.catalog.SourceMatchActivator
import com.yomira.app.i18n.LocaleManagerCompat
import com.yomira.app.update.AppUpdateChecker
import com.yomira.app.update.UpdateInfo
import com.yomira.core.database.dao.ChapterDao
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.SyncDao
import com.yomira.core.database.mapper.toModel
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.HomeRail
import com.yomira.core.model.HomeShelf
import com.yomira.core.model.RecommendedSeries
import com.yomira.core.model.RelatedSeries
import com.yomira.core.model.WatchableAdaptation
import com.yomira.data.metadata.SeriesConnectionRepository
import com.yomira.data.metadata.WaterfallMetadataAggregator
import com.yomira.domain.extension.*
import com.yomira.feature.details.ChapterUiState
import com.yomira.feature.home.ContinueReadingUi
import com.yomira.feature.home.buildHomeRails
import com.yomira.feature.home.LibraryUpdateUi
import com.yomira.feature.home.unreadLibraryChapters
import com.yomira.feature.home.chronologicalLibraryUpdates
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.Optional
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.yomira.core.ui.adaptationPosterLabel

data class CatalogState(val manga: List<Manga> = emptyList(), val updated: List<Manga> = emptyList(), val loading: Boolean = true, val error: String? = null)

/**
 * Append-only discovery window. [items] holds every page already fetched in
 * this session, [page] is the last loaded page (0 = none) and [hasMore] is the
 * provider's continuation signal. The grid renders `catalog.manga + items`
 * while Room re-emits the same rows, so a merge never doubles a card.
 */
data class DiscoveryState(
    val items: List<Manga> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

/** Status of the on-demand waterfall lookup for a query that is not in Room yet. */
data class LiveSearchState(
    val query: String = "",
    val searching: Boolean = false,
    val message: String? = null,
)

/**
 * Franchise relations and community recommendations of the open detail page.
 * Both showcases are progressive enhancement: a failed lookup only clears them.
 */
data class SeriesConnectionState(
    val loading: Boolean = false,
    val related: List<RelatedSeries> = emptyList(),
    val recommended: List<RecommendedSeries> = emptyList(),
    val failed: Boolean = false,
    val adaptations: List<WatchableAdaptation> = emptyList(),
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
    private val pagedCatalogLoader: PagedCatalogLoader,
    private val metadataAggregator: WaterfallMetadataAggregator,
    private val seriesConnections: SeriesConnectionRepository,
    private val updateChecker: AppUpdateChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val preferences = context.getSharedPreferences("yomira_ui", Context.MODE_PRIVATE)
    val catalog = MutableStateFlow(CatalogState())
    val columns = MutableStateFlow(preferences.getInt("columns", 3).coerceIn(2, 4))
    val vaultColumns = MutableStateFlow(preferences.getInt("vault_columns", 3).coerceIn(2, 4))
    val saved = MutableStateFlow(preferences.getStringSet("saved", emptySet())!!.toSet())
    val read = MutableStateFlow(preferences.getStringSet("read", emptySet())!!.toSet())
    private val dismissedResume = MutableStateFlow(preferences.getStringSet("dismissed_resume", emptySet())!!.toSet())
    val unlocked = MutableStateFlow(false)
    val match = MutableStateFlow<SmartMatchResult?>(null)
    val matching = MutableStateFlow(false)
    val liveSearch = MutableStateFlow(LiveSearchState())
    val connections = MutableStateFlow(SeriesConnectionState())
    val adaptationBadges = MutableStateFlow(preferences.all.entries.mapNotNull { (key, value) ->
        if (key.startsWith("poster_adaptation:") && value is String) key.removePrefix("poster_adaptation:") to value else null
    }.toMap())
    private val requestedPosterAdaptations = mutableSetOf<String>()
    private val posterLookupPermits = Semaphore(3)
    val libraryRefreshing = MutableStateFlow(false)
    val libraryRefreshError = MutableStateFlow<String?>(null)
    val update = MutableStateFlow<UpdateInfo?>(null)
    val infiniteLoading = MutableStateFlow(false)
    private var currentTrendingPage = 1
    /** Multi-select tag matrix state; ids resolved by `CatalogTags` in the UI layer. */
    val selectedTags = MutableStateFlow<List<String>>(emptyList())

    /** Pages appended to Home / Discover. */
    val discovery = MutableStateFlow(DiscoveryState())

    private val shelfSession = MutableStateFlow(Random.nextInt() to HomeShelf.select())

    /** Reading and saved-state changes promote learned interests without random shelf rolls. */
    val homeRails: StateFlow<List<HomeRail>> = combine(catalog, discovery, read, saved,
        combine(shelfSession, onboardingStore.observe()) { session, state -> session to state?.preferences }) {
        catalogState, discoveryState, readIds, savedIds, session ->
        buildHomeRails(
            catalog = catalogState.manga,
            updated = catalogState.updated,
            discovered = discoveryState.items,
            readIds = readIds,
            savedIds = savedIds,
            seed = session.first.first,
            favoriteSeries = session.second?.favoriteSeries.orEmpty(),
            legacyFavoriteGenres = session.second?.favoriteGenres.orEmpty(),
        )
    }.distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, shelfSession.value.second.map { HomeRail(it, emptyList()) })

    /**
     * Newest reader position across the library, resolved into a presentation
     * model for the Home resume capsule. Room emits on every saved page, so the
     * card tracks reading without a manual refresh.
     */
    val continueReading: StateFlow<ContinueReadingUi?> = combine(syncDao.observeLatestReaderProgress(), dismissedResume) { progress, dismissed ->
        progress?.takeUnless { "${it.chapterId}:${it.updatedAt}" in dismissed }
    }
        .mapLatest { progress ->
            if (progress == null) return@mapLatest null
            val manga = mangaDao.getById(progress.mangaId) ?: return@mapLatest null
            val chapter = chapterDao.getById(progress.chapterId)
            ContinueReadingUi(
                mangaId = manga.id,
                chapterId = progress.chapterId,
                title = manga.title,
                coverUrl = manga.coverUrl,
                chapterTitle = chapter?.title
                    ?: "Bölüm ${chapter?.chapterNumber ?: progress.chapterIndex + 1}",
                pageIndex = progress.pageIndex,
                pageCount = progress.pageCount,
                updatedAt = progress.updatedAt,
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Independent cursor for the +18 Vault; never mixed with the public feed. */
    val vaultDiscovery = MutableStateFlow(DiscoveryState())

    /** Subscribe only to saved titles; Room invalidation updates arrivals and read badges live. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val libraryUpdates: StateFlow<List<LibraryUpdateUi>> = combine(saved, catalog) { ids, state ->
        state.manga.filter { it.id in ids && it.contentRating != ContentRating.EXPLICIT }
    }.distinctUntilChanged().flatMapLatest { titles ->
        if (titles.isEmpty()) flowOf(emptyList())
        else combine(titles.map { manga ->
            combine(chapterDao.observeByMangaId(manga.id), syncDao.observeReaderProgress(manga.id), read) { entities, progress, readIds ->
                val chapters = entities.map { it.toModel() }
                val lastNumber = progress.mapNotNull { position -> chapters.firstOrNull { it.id == position.chapterId }?.chapterNumber }.maxOrNull()
                unreadLibraryChapters(chapters, readIds, lastNumber).map { LibraryUpdateUi(manga, it) }
            }
        }) { chronologicalLibraryUpdates(it.toList().flatten()) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var matchJob: Job? = null
    private var catalogJob: Job? = null
    private var searchJob: Job? = null
    private var connectionsJob: Job? = null
    private var pagingJob: Job? = null
    private var vaultPagingJob: Job? = null

    init {
        observeCatalog()
        bootstrapCatalog()
        checkForUpdates()
    }

    /** Toggles one tag id; order of first selection is preserved for stable chips. */
    fun toggleTag(id: String) {
        val current = selectedTags.value
        selectedTags.value = if (id in current) current - id else current + id
    }

    fun clearTags() { selectedTags.value = emptyList() }

    /** Small, cached enhancement of visible posters, never a lookup for the entire catalogue. */
    fun loadPosterAdaptations(items: List<Manga>) {
        items.take(12).filter { it.contentRating != ContentRating.EXPLICIT || unlocked.value }.forEach { manga ->
            if (manga.id in adaptationBadges.value || !requestedPosterAdaptations.add(manga.id)) return@forEach
            viewModelScope.launch {
                try {
                    val adaptation = posterLookupPermits.withPermit {
                        withContext(Dispatchers.IO) { seriesConnections.connectionsFor(manga).adaptations.firstOrNull() }
                    }
                    adaptation?.let { rememberPosterAdaptation(manga.id, it) }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { /* A missing badge must never block the catalogue. */ }
            }
        }
    }

    private fun rememberPosterAdaptation(id: String, adaptation: WatchableAdaptation) {
        val label = adaptationPosterLabel(adaptation.kind, adaptation.format)
        adaptationBadges.value = adaptationBadges.value + (id to label)
        persist { putString("poster_adaptation:$id", label) }
    }

    fun refresh() {
        shelfSession.value = Random.nextInt() to HomeShelf.select()
        observeCatalog()
    }

    /** Refresh references, not pages. Keep the current source so read chapter IDs stay stable. */
    fun refreshLibraryUpdates() {
        if (libraryRefreshing.value) return
        if (!matcher.isPresent) {
            libraryRefreshError.value = context.getString(R.string.library_refresh_failed)
            return
        }
        libraryRefreshing.value = true
        libraryRefreshError.value = null
        val titles = catalog.value.manga.filter { it.id in saved.value && it.contentRating != ContentRating.EXPLICIT }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stored = onboardingStore.read()?.preferences
                    val language = stored?.language ?: activeReadingLanguage()
                    val preferences = (stored ?: ReaderPreferences(language)).copy(language = language)
                    for (manga in titles) {
                        try {
                            val sourcePreferences = com.yomira.app.catalog.readingSourcePreferences(manga, preferences, unlocked = false)
                                ?: continue
                            val result = matcher.get().match(SeriesIdentity(manga.id, manga.title, manga.altTitles.toSet()), sourcePreferences)
                            if (result is SmartMatchResult.Ready && result.canRead) {
                                val current = chapterDao.getByMangaId(manga.id).firstOrNull()?.url?.let(SourceChapterLocation::decode)
                                if (current == null || (current.packageName == result.selected.extension.packageName && current.sourceId == result.selected.source.id)) {
                                    SourceMatchActivator(chapterDao).activate(manga.id, result)
                                } else libraryRefreshError.value = context.getString(R.string.library_refresh_failed)
                            } else libraryRefreshError.value = context.getString(R.string.library_refresh_failed)
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { libraryRefreshError.value = context.getString(R.string.library_refresh_failed) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { libraryRefreshError.value = context.getString(R.string.library_refresh_failed)
            } finally { libraryRefreshing.value = false }
        }
    }

    private fun observeCatalog() {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            catalog.value = catalog.value.copy(loading = true, error = null)
            combine(mangaDao.observeAll(), mangaDao.observeRecentlyUpdated()) { all, updated ->
                CatalogState(all.map { it.toModel() }, updated.map { it.toModel() }, loading = false)
            }.catch { error ->
                emit(catalog.value.copy(loading = false, error = error.message ?: context.getString(R.string.catalog_load_failed)))
            }.flowOn(Dispatchers.IO).collect { catalog.value = it }
        }
    }

    /**
     * First launch: Room is filled from the bundled catalogue, then (only when
     * the network happens to be reachable) trending titles are appended.
     * Both steps are best-effort; failure must leave the seeded grid intact.
     */
    private fun bootstrapCatalog() {
        viewModelScope.launch {
            // Seeding parses the bundled JSON and writes Room rows; trending parses the
            // AniList/MangaDex payloads. Both block for seconds on a cold start, so they
            // run on IO and only the resulting state publication stays on Main.
            withContext(Dispatchers.IO) {
                catalogSeeder.seedIfEmpty()
                try {
                    mangaDao.observeAll().first().forEach { entity ->
                        val rating = CatalogMerger.reclassifiedRating(entity)
                        if (rating != entity.contentRating) mangaDao.updateContentRating(entity.id, rating)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A temporarily unavailable database must not crash catalog bootstrap.
                }
            }
            // The existing Room observer sees seeded rows; do not reroll shelves
            // during bootstrap after the first frame has already been shown.
            if (!hasInternet()) return@launch
            try {
                withContext(Dispatchers.IO) { trendingCatalogLoader.refresh() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Trending is a bonus; the seeded catalogue is already on screen.
            }
        }
    }

    /**
     * Fetches the next AniList/MangaDex/ComicVine/OpenLibrary window and appends
     * it to the requested grid. Safe to call from every `LaunchedEffect` at the
     * bottom of the list: concurrent calls are collapsed and an exhausted feed
     * stops issuing requests.
     */
    fun loadNextPage(adult: Boolean = false) {
        if (adult && !unlocked.value) return
        val state = if (adult) vaultDiscovery else discovery
        val snapshot = state.value
        if (snapshot.loading || !snapshot.hasMore) return
        val active = if (adult) vaultPagingJob else pagingJob
        if (active?.isActive == true) return

        val job = viewModelScope.launch {
            state.value = snapshot.copy(loading = true, error = null)
            try {
                val page = withContext(Dispatchers.IO) {
                    pagedCatalogLoader.load(snapshot.page + 1, adult = adult)
                }
                // Room has already emitted most of these rows; only genuinely new
                // persisted ids are appended so the grid never shows a card twice.
                val known = HashSet<String>(page.items.size + catalog.value.manga.size)
                catalog.value.manga.forEach { known += it.id }
                state.value.items.forEach { known += it.id }
                val fresh = page.items.filter { known.add(it.id) }
                state.value = state.value.copy(
                    items = state.value.items + fresh,
                    page = page.page,
                    loading = false,
                    hasMore = page.hasMore,
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(
                    loading = false,
                    error = context.getString(R.string.paging_failed),
                )
            }
        }
        if (adult) vaultPagingJob = job else pagingJob = job
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
                    liveSearch.value = LiveSearchState(trimmed, message = context.getString(R.string.live_search_no_results))
                    return@launch
                }
                if (found.contentRating == ContentRating.EXPLICIT && !unlocked.value) {
                    liveSearch.value = LiveSearchState(trimmed, message = context.getString(R.string.live_search_vault_only))
                    return@launch
                }
                // Merge-before-insert: a series stored under its Japanese/romaji name is
                // updated in place, so the home grid never shows the same story twice.
                withContext(Dispatchers.IO) { CatalogMerger.upsertMerged(mangaDao, found) }
                liveSearch.value = LiveSearchState(trimmed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                liveSearch.value = LiveSearchState(trimmed, message = context.getString(R.string.live_search_unavailable))
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
                // Network I/O: keep the GitHub call off the UI thread.
                withContext(Dispatchers.IO) { updateChecker.check() }
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

    /** Hide this saved position, without throwing away the reader's page. A new read resurfaces it. */
    fun dismissContinueReading(item: ContinueReadingUi) {
        dismissedResume.value = dismissedResume.value + "${item.chapterId}:${item.updatedAt}"
        persist { putStringSet("dismissed_resume", dismissedResume.value) }
    }

    fun markSeriesRead(mangaId: String) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) { chapterDao.getByMangaId(mangaId).map { it.id } }
            read.value = read.value + ids + mangaId
            persist { putStringSet("read", read.value) }
        }
    }

    fun markReadThrough(chapter: com.yomira.core.model.Chapter) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) {
                chapterDao.getByMangaId(chapter.mangaId).filter { it.chapterNumber <= chapter.chapterNumber }.map { it.id }
            }
            read.value = read.value + ids
            persist { putStringSet("read", read.value) }
        }
    }

    fun resetProgress(mangaId: String) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) {
                syncDao.resetProgress(mangaId)
                chapterDao.getByMangaId(mangaId).map { it.id }.toSet()
            }
            read.value = read.value - ids - mangaId
            persist { putStringSet("read", read.value) }
        }
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
                // Room suspend queries must never resolve on the Main dispatcher when
                // they gate navigation: the resume tap has to stay responsive.
                withContext(Dispatchers.IO) {
                    val previous = syncDao.latestReaderProgress(mangaId)
                    previous?.chapterId?.takeIf { chapterDao.getById(it)?.mangaId == mangaId } ?: fallbackId
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { fallbackId }
            onReady(chapterId)
        }
    }

    fun clearMatch() { matchJob?.cancel(); match.value = null; matching.value = false }

    fun lock() { unlocked.value = false; clearMatch() }

    /** Cached chapters do not bypass fresh installed-source ranking. */
    suspend fun ensureSource(manga: Manga) {
        if (manga.contentRating == ContentRating.EXPLICIT && !unlocked.value) return
        findSource(manga)
    }

    suspend fun resolveReadingChapter(manga: Manga, requestedId: String): String? {
        if (!matcher.isPresent || (manga.contentRating == ContentRating.EXPLICIT && !unlocked.value)) return null
        // Snapshot before waiting: detail activation can replace source-specific chapter IDs.
        val requestedNumber = withContext(Dispatchers.IO) {
            chapterDao.getById(requestedId)?.takeIf { it.mangaId == manga.id }?.chapterNumber
        }
        matchJob?.join()
        return withContext(Dispatchers.IO) {
            val stored = onboardingStore.read()?.preferences ?: ReaderPreferences(activeReadingLanguage())
            val sourcePreferences = com.yomira.app.catalog.readingSourcePreferences(manga, stored, unlocked.value)
                ?: return@withContext null
            com.yomira.app.catalog.ReadingSourceResolver(matcher.get(), chapterDao).resolve(manga, requestedId,
                sourcePreferences, requestedNumber)
        }
    }

    fun findSource(manga: Manga) {
        if (manga.contentRating == ContentRating.EXPLICIT && !unlocked.value) return
        clearMatch()
        matchJob = viewModelScope.launch {
            matching.value = true
            try {
                match.value = if (matcher.isPresent) withContext(Dispatchers.IO) {
                    // Source matching follows the language the reader picked when setting
                    // up the app. Only when no onboarding state exists yet (corrupted or
                    // pre-onboarding install) do we derive it from the active app locale,
                    // instead of hardcoding Turkish and silently dropping English sources.
                    val stored = onboardingStore.read()?.preferences
                    val language = stored?.language ?: activeReadingLanguage()
                    val settings = stored?.copy(language = language) ?: ReaderPreferences(language)
                    val sourcePreferences = com.yomira.app.catalog.readingSourcePreferences(manga, settings, unlocked.value)
                        ?: return@withContext SmartMatchResult.NoMatch
                    val result = matcher.get().match(SeriesIdentity(manga.id, manga.title, manga.altTitles.toSet()),
                        sourcePreferences)
                    if (result is SmartMatchResult.Ready) {
                        // Activate before publishing: opening a series never asks the reader
                        // to choose a match or switch away from the onboarding language.
                        SourceMatchActivator(chapterDao).activate(manga.id, result)
                        result.copy(alternatives = emptyList())
                    } else if (result is SmartMatchResult.MissingExtension) {
                        result.copy(alternatives = emptyList())
                    } else result
                } else SmartMatchResult.Unavailable(listOf(SourceFailure("runtime", context.getString(R.string.match_engine_missing))))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                match.value = SmartMatchResult.Unavailable(listOf(SourceFailure("runtime", error.message ?: context.getString(R.string.match_unreachable))))
            } finally { matching.value = false }
        }
    }

    /**
     * Reading language used when the onboarding state is unavailable. Mirrors the
     * active app locale (explicit in-app choice first, then device language) so a
     * fresh/legacy install never falls back to a hardcoded language.
     */
    private fun activeReadingLanguage(): ReadingLanguage {
        val tag = LocaleManagerCompat.currentApplicationTag()
        return ReadingLanguage.entries.firstOrNull { it.code.equals(tag, ignoreCase = true) }
            ?: ReadingLanguage.ENGLISH
    }

    /**
     * Franchise relations and community recommendations of the open detail page,
     * fetched on demand from the AniList relations graph. Cancels the previous
     * lookup so a fast page-to-page navigation never shows the wrong showcase.
     */
    fun loadConnections(manga: Manga) {
        connectionsJob?.cancel()
        connectionsJob = viewModelScope.launch {
            connections.value = SeriesConnectionState(loading = true)
            connections.value = try {
                val found = withContext(Dispatchers.IO) { seriesConnections.connectionsFor(manga) }
                found.adaptations.firstOrNull()?.let { adaptation ->
                    rememberPosterAdaptation(manga.id, adaptation)
                }
                SeriesConnectionState(related = found.related, recommended = found.recommended, adaptations = found.adaptations)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Progressive enhancement: the chapter list must stay usable when
                // the relations graph is unreachable.
                SeriesConnectionState(failed = true)
            }
        }
    }

    /**
     * Opens a connected series from the detail showcases. A shelf card is reused
     * as-is; otherwise the waterfall imports the series metadata first
     * (merge-before-insert) so the target detail page has a card to render.
     * The callback receives the shelf id, or null when the series could not be
     * opened (no metadata match, or an adult series behind the vault gate).
     */
    fun openSeries(id: String, title: String, onReady: (String?) -> Unit) {
        viewModelScope.launch {
            val local = catalog.value.manga.firstOrNull { it.id == id }
                ?: catalog.value.manga.firstOrNull { it.matchesTitle(title) }
            if (local != null) {
                onReady(local.id)
                return@launch
            }
            val imported = try {
                withContext(Dispatchers.IO) { metadataAggregator.aggregate(title) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            // An adult series must never slip past the vault gate through a recommendation.
            if (imported == null || (imported.contentRating == ContentRating.EXPLICIT && !unlocked.value)) {
                onReady(null)
                return@launch
            }
            val stored = withContext(Dispatchers.IO) {
                CatalogMerger.upsertMergedAndReturn(mangaDao, imported).entity
            }
            // The detail route renders from the Room-backed catalog, so navigation
            // waits for the freshly imported card to reach the shelf.
            withTimeoutOrNull(CATALOG_EMISSION_TIMEOUT_MS) {
                catalog.first { state -> state.manga.any { it.id == stored.id } }
            }
            onReady(stored.id)
        }
    }

    private fun Manga.matchesTitle(title: String): Boolean =
        this.title.equals(title, true) || altTitles.any { it.equals(title, true) }

    private companion object {
        const val MIN_LIVE_QUERY_LENGTH = 3
        const val LIVE_SEARCH_DEBOUNCE_MS = 650L
        const val CATALOG_EMISSION_TIMEOUT_MS = 2_000L
        const val KEY_DISMISSED_UPDATE = "dismissed_update_version"
    }
}
