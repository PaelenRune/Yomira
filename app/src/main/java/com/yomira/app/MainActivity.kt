package com.yomira.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yomira.app.catalog.catalogTagChips
import com.yomira.app.extensions.ExtensionsScreen
import com.yomira.app.extensions.ExtensionsViewModel
import com.yomira.app.achievement.AchievementBannerHost
import com.yomira.domain.journey.JourneyRepository
import javax.inject.Inject
import com.yomira.app.i18n.LocaleManagerCompat
import com.yomira.app.onboarding.OnboardingViewModel
import com.yomira.app.settings.SettingsViewModel
import com.yomira.app.settings.SettingsMaintenanceViewModel
import com.yomira.app.update.InAppUpdateDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.domain.extension.RepoDeepLink
import com.yomira.domain.extension.RepoDeepLinkResult
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.hasExcludedTags
import com.yomira.core.model.isVisibleForReader
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.YomiraTheme
import com.yomira.feature.details.DetailCopy
import com.yomira.feature.home.*
import com.yomira.feature.journey.JourneyCopy
import com.yomira.feature.journey.JourneyViewModel
import com.yomira.feature.journey.ProfileJourneyScreen
import com.yomira.feature.journey.WrappedCopy
import com.yomira.feature.journey.WrappedScreen
import com.yomira.feature.reader.ReaderCopy
import com.yomira.feature.reader.ReaderScreen
import com.yomira.feature.reader.ReaderViewModel
import com.yomira.feature.settings.*
import com.yomira.feature.vault.VaultCopy
import com.yomira.feature.vault.VaultScreen
import com.yomira.feature.details.MangaDetailScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var journeyRepository: JourneyRepository
    @Inject lateinit var extensionRepository: ExtensionRepository
    @Inject lateinit var extensionConsent: com.yomira.app.extensions.ExtensionConsentCoordinator
    private var pendingRepoLink by mutableStateOf<RepoDeepLinkResult>(RepoDeepLinkResult.NotRepoLink)
    private var navigateExtensions by mutableStateOf(false)
    private var addingRepo by mutableStateOf(false)
    @Inject lateinit var upgradeCleanup: com.yomira.app.migration.UpgradeCleanupManager
    private val model: YomiraViewModel by viewModels()
    private val readerModel: ReaderViewModel by viewModels()
    private val onboardingModel: OnboardingViewModel by viewModels()
    private val settingsModel: SettingsViewModel by viewModels()
    private val maintenanceModel: SettingsMaintenanceViewModel by viewModels()
    private val backupModel: com.yomira.app.backup.LocalBackupViewModel by viewModels()
    private val journeyModel: JourneyViewModel by viewModels()
    private val extensionsModel: ExtensionsViewModel by viewModels()
    private var authError by mutableStateOf<String?>(null)
    private var confirmingCredential = false
    /** Set while a settings action (e.g. revealing the Vault) awaits the credential verdict. */
    private var credentialResult: ((Boolean) -> Unit)? = null
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        confirmingCredential = false
        val verified = result.resultCode == Activity.RESULT_OK
        val callback = credentialResult
        credentialResult = null
        if (callback != null) callback(verified) else onVaultUnlockResult(verified)
    }

    private fun onVaultUnlockResult(verified: Boolean) {
        model.unlocked.value = verified
        authError = if (verified) null else getString(R.string.vault_auth_incomplete)
    }

    /** One reusable device-credential gate for both the Vault entry and the settings reveal flow. */
    @Suppress("DEPRECATION")
    private fun confirmCredentials(title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        if (confirmingCredential) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
        if (intent == null) {
            authError = getString(R.string.vault_no_screen_lock)
            onResult(false)
            return
        }
        confirmingCredential = true
        credentialResult = onResult
        authentication.launch(intent)
    }

    private fun unlock() = confirmCredentials(
        getString(R.string.vault_unlock_prompt_title),
        getString(R.string.vault_unlock_prompt_body),
    ) { verified -> onVaultUnlockResult(verified) }

    override fun onStop() {
        // The credential activity itself must not invalidate an authentication request.
        // The Vault only re-locks in the background while the user asked for that guard.
        if (!confirmingCredential && settingsModel.settings.value.lockOnBackground) model.lock()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receiveRepoIntent(intent)
        else {
            pendingRepoLink = savedInstanceState.getString("pendingRepo")?.let { RepoDeepLinkResult.Valid(it) }
                ?: if (savedInstanceState.getBoolean("invalidRepo")) RepoDeepLinkResult.Invalid else RepoDeepLinkResult.NotRepoLink
            navigateExtensions = savedInstanceState.getBoolean("navigateExtensions")
        }
        // Also hides all app previews in Recents; no sensitive frame can leak during navigation.
        // Debug builds stay capturable: FLAG_SECURE makes `adb exec-out screencap` return an
        // empty PNG, which would hide real crashes from the QA screenshot harness.
        if (!BuildConfig.DEBUG && !BuildConfig.QA_CAPTURE) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { YomiraTheme {
            com.yomira.app.extensions.ExtensionConsentDialog(extensionConsent)
            val onboarding by onboardingModel.state.collectAsStateWithLifecycle()
            val onboardingCatalog by model.catalog.collectAsStateWithLifecycle()
            // The reading language chosen in the wizard is also the UI language: applying
            // it through LocaleManagerCompat recreates this activity with the new resource
            // locale, so every string switches to English/Turkish instantly. The startup
            // guard applies it at most once per process, because Samsung One UI 6 can keep
            // reporting the old locale while recreating, which would otherwise loop forever.
            LaunchedEffect(onboarding.loading, onboarding.completed, onboarding.languageCode) {
                if (!onboarding.loading) {
                    val language = if (onboarding.completed) onboarding.languageCode
                        else LocaleManagerCompat.currentApplicationTag()
                    LocaleManagerCompat.syncApplicationLocalesOnStartup(LocaleManagerCompat.supportedTag(language))
                }
            }
            Box(Modifier.fillMaxSize()) {
            when {
                onboarding.loading -> Surface(color = Color(0xFF0D0D12), modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (onboarding.error == null) CircularProgressIndicator()
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(localizedOnboardingError(onboarding.error).orEmpty())
                            TextButton(onClick = onboardingModel::load) { Text(stringResource(R.string.retry_action)) }
                        }
                    }
                }
                !onboarding.completed -> OnboardingScreen(
                    onboarding.saving, localizedOnboardingError(onboarding.error),
                    onComplete = { language, adult, excluded, favorites, series ->
                        onboardingModel.complete(language, adult, excluded, favorites, series)
                        LocaleManagerCompat.setApplicationLocales(language)
                    },
                    copy = onboardingCopy(), initialLanguage = LocaleManagerCompat.currentApplicationTag(),
                    onLanguageSelected = { LocaleManagerCompat.setApplicationLocales(it) },
                    onCompleteWithRepository = { language, adult, excluded, favorites, series ->
                        onboardingModel.complete(language, adult, excluded, favorites, series, integrateRepository = true)
                    },
                    onOpenRepository = { openReference(OnboardingWizardManager.KEIYOUSHI_WEBPAGE) },
                    catalog = onboardingCatalog.manga,
                )
                else -> {
                    val settings by settingsModel.settings.collectAsStateWithLifecycle()
                    YomiraNavigation(
                        model, readerModel, settingsModel, maintenanceModel, backupModel, journeyModel, extensionsModel, settings, authError,
                        onUnlockVault = {
                            if (settings.vaultVisible && maintenanceModel.state.value.readerPreferences?.let { it.isAdult && it.allowAdultContent } == true) {
                                // "Protect with device lock" is the user's call; default is on.
                                if (settings.requireDeviceLock) unlock() else model.unlocked.value = true
                            } else authError = getString(R.string.vault_adult_disabled)
                        },
                        onRevealVault = {
                            confirmCredentials(
                                getString(R.string.settings_reveal_prompt_title),
                                getString(R.string.settings_reveal_prompt_body),
                            ) { verified -> settingsModel.onVaultRevealResult(verified) }
                        },
                        onVaultNsfwConsent = settingsModel::setVaultNsfwConsent,
                        ::openReference, navigateExtensions, { navigateExtensions = false },
                    )
                }
            }
            AchievementBannerHost(journeyRepository.unlockEvents)
            if (!onboarding.loading && onboarding.completed) when (val link = pendingRepoLink) {
                is RepoDeepLinkResult.Valid -> AlertDialog(
                    onDismissRequest = { if (!addingRepo) pendingRepoLink = RepoDeepLinkResult.NotRepoLink },
                    title = { Text(stringResource(R.string.repo_confirm_title)) },
                    text = { Text(link.indexUrl) },
                    confirmButton = { TextButton(enabled = !addingRepo, onClick = {
                        addingRepo = true
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { extensionRepository.addRepository(link.indexUrl) }
                                pendingRepoLink = RepoDeepLinkResult.NotRepoLink
                                navigateExtensions = true
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                            } catch (_: Exception) { pendingRepoLink = RepoDeepLinkResult.Invalid
                            } finally { addingRepo = false }
                        }
                    }) { Text(stringResource(R.string.settings_add)) } },
                    dismissButton = { TextButton(enabled = !addingRepo, onClick = { pendingRepoLink = RepoDeepLinkResult.NotRepoLink }) { Text(stringResource(R.string.settings_cancel)) } },
                )
                RepoDeepLinkResult.Invalid -> AlertDialog(
                    onDismissRequest = { pendingRepoLink = RepoDeepLinkResult.NotRepoLink },
                    title = { Text(stringResource(R.string.repo_error_title)) },
                    text = { Text(stringResource(R.string.repo_error_body)) },
                    confirmButton = { TextButton(onClick = { pendingRepoLink = RepoDeepLinkResult.NotRepoLink }) { Text(stringResource(R.string.settings_cancel)) } },
                )
                RepoDeepLinkResult.NotRepoLink -> Unit
            }
            var showUpgradeNotice by remember { mutableStateOf(upgradeCleanup.noticePending()) }
            val upgradeNoticeVisible = com.yomira.app.migration.shouldShowUpgradeNotice(
                showUpgradeNotice, onboarding.loading,
                onboarding.completed && pendingRepoLink != RepoDeepLinkResult.NotRepoLink,
            )
            LaunchedEffect(showUpgradeNotice, onboarding.loading, onboarding.completed, pendingRepoLink) {
                if (upgradeNoticeVisible) {
                    android.util.Log.d("YomiraUpgrade", "v811-notice shown")
                }
            }
            if (upgradeNoticeVisible) AlertDialog(
                onDismissRequest = { upgradeCleanup.dismissNotice(); showUpgradeNotice = false },
                text = { Text(stringResource(R.string.upgrade_sources_notice)) },
                confirmButton = { TextButton(onClick = {
                    upgradeCleanup.dismissNotice(); showUpgradeNotice = false
                }) { Text(stringResource(android.R.string.ok)) } },
            )
            }
        } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveRepoIntent(intent)
    }

    private fun receiveRepoIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val result = RepoDeepLink.parse(intent.dataString)
        if (result != RepoDeepLinkResult.NotRepoLink) pendingRepoLink = result
    }

    override fun onSaveInstanceState(outState: Bundle) {
        (pendingRepoLink as? RepoDeepLinkResult.Valid)?.let { outState.putString("pendingRepo", it.indexUrl) }
        outState.putBoolean("invalidRepo", pendingRepoLink == RepoDeepLinkResult.Invalid)
        outState.putBoolean("navigateExtensions", navigateExtensions)
        super.onSaveInstanceState(outState)
    }

    private fun openReference(url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return false
        return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true } catch (_: android.content.ActivityNotFoundException) { false }
    }
}

/** Maps the view model's stable error codes to the strings of the active language. */
@Composable
private fun localizedOnboardingError(code: String?): String? = when (code) {
    OnboardingViewModel.ERROR_LOAD -> stringResource(R.string.onboarding_failed_load)
    OnboardingViewModel.ERROR_SAVE -> stringResource(R.string.onboarding_failed_save)
    else -> code
}

@Composable
private fun YomiraNavigation(
    model: YomiraViewModel,
    readerModel: ReaderViewModel,
    settingsModel: SettingsViewModel,
    maintenanceModel: SettingsMaintenanceViewModel,
    backupModel: com.yomira.app.backup.LocalBackupViewModel,
    journeyModel: JourneyViewModel,
    extensionsModel: ExtensionsViewModel,
    settings: AppSettings,
    authError: String?,
    onUnlockVault: () -> Unit,
    onRevealVault: () -> Unit,
    onVaultNsfwConsent: (Boolean) -> Unit,
    openReference: (String) -> Boolean,
    navigateExtensions: Boolean,
    onExtensionsNavigated: () -> Unit,
) {
    val nav = rememberNavController()
    LaunchedEffect(navigateExtensions) {
        if (navigateExtensions) {
            nav.navigate("extensions") { launchSingleTop = true }
            onExtensionsNavigated()
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val maintenanceState by maintenanceModel.state.collectAsStateWithLifecycle()
    val preferences = maintenanceState.readerPreferences ?: ReaderPreferences(ReadingLanguage.TURKISH)
    fun allowed(manga: Manga) = maintenanceState.readerPreferences != null && manga.isVisibleForReader(preferences.excludedTags, preferences.isAdult, preferences.allowAdultContent)
    val vaultEnabled = settings.vaultVisible && preferences.isAdult && preferences.allowAdultContent
    val rawCatalog by model.catalog.collectAsStateWithLifecycle()
    val catalog = remember(rawCatalog, preferences, maintenanceState.readerPreferences != null) {
        rawCatalog.copy(manga = rawCatalog.manga.filter(::allowed), updated = rawCatalog.updated.filter(::allowed))
    }
    val rawHomeRails by model.homeRails.collectAsStateWithLifecycle()
    val homeRails = remember(rawHomeRails, preferences, maintenanceState.readerPreferences != null) {
        rawHomeRails.map { it.copy(items = it.items.filter(::allowed)) }
    }
    val columns by model.columns.collectAsStateWithLifecycle()
    val vaultColumns by model.vaultColumns.collectAsStateWithLifecycle()
    val unlocked by model.unlocked.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val rawLibraryUpdates by model.libraryUpdates.collectAsStateWithLifecycle()
    val libraryUpdates = remember(rawLibraryUpdates, preferences, maintenanceState.readerPreferences != null) {
        rawLibraryUpdates.filter { allowed(it.manga) && it.manga.contentRating != ContentRating.EXPLICIT }
    }
    val libraryRefreshing by model.libraryRefreshing.collectAsStateWithLifecycle()
    val libraryRefreshError by model.libraryRefreshError.collectAsStateWithLifecycle()
    val adaptationBadges by model.adaptationBadges.collectAsStateWithLifecycle()
    val readingIds by model.readingIds.collectAsStateWithLifecycle()
    val read by model.read.collectAsStateWithLifecycle()
    val matching by model.matching.collectAsStateWithLifecycle()
    val liveSearch by model.liveSearch.collectAsStateWithLifecycle()
    val selectedTags by model.selectedTags.collectAsStateWithLifecycle()
    val rawDiscovery by model.discovery.collectAsStateWithLifecycle()
    val discovery = remember(rawDiscovery, preferences, maintenanceState.readerPreferences != null) {
        rawDiscovery.copy(items = rawDiscovery.items.filter(::allowed))
    }
    val rawVaultDiscovery by model.vaultDiscovery.collectAsStateWithLifecycle()
    val vaultDiscovery = remember(rawVaultDiscovery, preferences, maintenanceState.readerPreferences != null) {
        rawVaultDiscovery.copy(items = rawVaultDiscovery.items.filter(::allowed))
    }
    val pendingUpdate by model.update.collectAsStateWithLifecycle()
    val settingsState by settingsModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val current = backStack?.destination?.route
    val context = LocalContext.current
    var actionManga by remember(current, unlocked, settings.vaultVisible) { mutableStateOf<Manga?>(null) }
    val shareLabel = stringResource(R.string.control_share)
    actionManga?.takeIf { allowed(it) && (it.contentRating != ContentRating.EXPLICIT || (unlocked && vaultEnabled)) }?.let { manga ->
        QuickActionSheet(manga.title, { actionManga = null }, listOf(
            stringResource(if (manga.id in saved) R.string.detail_save_remove else R.string.detail_save_add) to { model.toggleSaved(manga.id) },
            stringResource(R.string.detail_mark_read) to { model.markSeriesRead(manga.id) },
            stringResource(R.string.control_reset_progress) to { model.resetProgress(manga.id) },
            shareLabel to {
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, manga.title) }
                context.startActivity(Intent.createChooser(intent, shareLabel))
            },
        ))
    }
    val catalogCopy = catalogCopy()
    val openSeriesFailed = stringResource(R.string.detail_open_series_failed)
    val tagOptions = catalogTagChips()
    fun home() { nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = false }; launchSingleTop = true } }
    fun panic() { model.lock(); home() }
    fun details(manga: Manga) {
        if (!allowed(manga)) return
        if (manga.contentRating == ContentRating.EXPLICIT && !unlocked) return
        model.clearMatch()
        nav.navigate("details/${Uri.encode(manga.id)}")
    }
    pendingUpdate?.let { info ->
        InAppUpdateDialog(
            info = info,
            onDismiss = model::dismissUpdate,
            copy = updateDialogCopy(),
        )
    }
    // A locked adult detail is removed, not just covered with a lock overlay.
    LaunchedEffect(unlocked, current, catalog.manga) {
        val id = backStack?.arguments?.getString("id") ?: backStack?.arguments?.getString("mangaId")
        if (!unlocked && (current == "details/{id}" || current == "reader/{mangaId}/{chapterId}") && catalog.manga.any { it.id == id && it.contentRating == ContentRating.EXPLICIT }) panic()
    }
    // Hiding the Vault must also lock it and leave its route: no back-stack entry
    // may keep adult content reachable once the tab is gone.
    LaunchedEffect(vaultEnabled) {
        if (!vaultEnabled) {
            model.lock()
            if (nav.currentDestination?.route == "vault") home()
        }
    }
    LaunchedEffect(preferences, current) {
        val id = backStack?.arguments?.getString("id") ?: backStack?.arguments?.getString("mangaId")
        val item = (rawCatalog.manga + rawDiscovery.items + rawVaultDiscovery.items).firstOrNull { it.id == id }
        if (item != null && !allowed(item)) panic()
    }
    val showChrome = current == "home" || current == "discover" || current == "library" || current == "profile" || current == "vault"
    val showNavigation = current != "details/{id}" && current != "reader/{mangaId}/{chapterId}" && current != "settings" && current != "extensions" && current != "wrapped"
    // Tablet shell: at >=600dp the bottom bar becomes a left rail and the detail
    // screen switches to its 2-pane master-detail geometry (AdaptiveLayout).
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowSizeClass = AdaptiveLayout.windowSizeClass(windowWidth)
        val railLayout = AdaptiveLayout.usesNavigationRail(windowWidth.value)
        val twoPaneDetail = AdaptiveLayout.prefersTwoPane(windowWidth.value)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if (showChrome) {
            Surface(color = Color.Transparent) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguageSwitchButton { LocaleManagerCompat.toggleApplicationLocale() }
                    TextButton(onClick = { nav.navigate("extensions") { launchSingleTop = true } }) {
                        Text(stringResource(R.string.extensions_title))
                    }
                    FilledTonalButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_title))
                    }
                }
            }
        }
    }, bottomBar = {
        if (!railLayout && showNavigation) {
            YomiraBottomNavBar(current, onSelect = { destination ->
                if (destination != MainDestination.VAULT) model.lock()
                nav.navigate(destination.route) { popUpTo("home") { inclusive = false }; launchSingleTop = true }
            }, copy = catalogCopy, vaultVisible = vaultEnabled)
        }
    }) { padding ->
        Row(Modifier.fillMaxSize()
            .then(if (current == "reader/{mangaId}/{chapterId}" || current == "wrapped") Modifier else Modifier.padding(padding))) {
            if (railLayout && showNavigation) {
                YomiraNavigationRail(current, onSelect = { destination ->
                    if (destination != MainDestination.VAULT) model.lock()
                    nav.navigate(destination.route) { popUpTo("home") { inclusive = false }; launchSingleTop = true }
                }, copy = catalogCopy, vaultVisible = vaultEnabled)
            }
            NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f).fillMaxSize()) {
            listOf("home", "discover", "library").forEach { route ->
                composable(route) {
                    var query by rememberSaveable { mutableStateOf("") }
                    val paging = if (route == "library") DiscoveryState(hasMore = false) else discovery
                    // v8.2.0 placement matrix: safe mode keeps the public grid SAFE-only;
                    // adults additionally see MATURE series. EXPLICIT never leaks here
                    // (the v8.1.4 public-surface guarantee). The library shows what the
                    // user deliberately saved; only the blacklist still filters it there.
                    val items = if (route == "library") catalog.manga.filter { it.id in saved && !it.hasExcludedTags(preferences.excludedTags) }
                        else publicCatalog(catalog.manga, preferences.excludedTags, preferences.isAdult)
                    // Reading counts as membership before saving: a series the user is
                    // reading (reader progress exists) stays findable from the library
                    // search even when it was never added to the shelf. The public
                    // placement matrix still applies to these non-shelf results.
                    val searchBeyond = if (route == "library") catalog.manga.filter { it.id in readingIds && it.id !in saved }
                        else emptyList()
                    val resume by model.continueReading.collectAsStateWithLifecycle()
                    val resumeItem = resume?.takeIf { entry ->
                        catalog.manga.firstOrNull { it.id == entry.mangaId }?.let { allowed(it) && it.contentRating != ContentRating.EXPLICIT } == true
                    }
                    val seriesContent: @Composable () -> Unit = { HomeScreen(
                        HomeUiState(
                            manga = items,
                            trending = items.sortedByDescending { it.score },
                            updated = if (route == "library") catalog.updated.filter { it.id in saved && !it.hasExcludedTags(preferences.excludedTags) }
                                else publicCatalog(catalog.updated, preferences.excludedTags, preferences.isAdult),
                            columns = columns,
                            query = query,
                            loading = catalog.loading,
                            error = catalog.error,
                            searching = liveSearch.searching,
                            searchMessage = liveSearch.message,
                            selectedTags = selectedTags,
                            discovery = if (route == "library") emptyList() else paging.items,
                            loadingMore = paging.loading,
                            hasMore = paging.hasMore,
                            pagingError = paging.error,
                            readIds = read,
                            savedIds = saved,
                            rails = homeRails,
                            adaptationBadges = adaptationBadges,
                            unreadCounts = if (route == "library") libraryUpdates.groupingBy { it.manga.id }.eachCount() else emptyMap(),
                            excludedTags = preferences.excludedTags,
                            isAdult = preferences.isAdult,
                            searchBeyond = searchBeyond,
                        ),
                        onQuery = { value -> query = value; model.onQueryChanged(value) }, onColumns = { model.setColumns(it) }, onManga = ::details, onRetry = model::refresh,
                        title = when (route) { "discover" -> stringResource(R.string.title_discover); "library" -> stringResource(R.string.title_library); else -> stringResource(R.string.app_name) },
                        showHero = route == "home",
                        tagOptions = tagOptions,
                        onToggleTag = model::toggleTag,
                        onClearTags = model::clearTags,
                        copy = homeCopy(catalogCopy),
                        onLoadMore = { if (route != "library") model.loadNextPage() },
                        sizeClass = windowSizeClass,
                        continueReading = if (route == "home") resumeItem else null,
                        onDismissContinueReading = model::dismissContinueReading,
                        onResetProgress = model::resetProgress,
                        onMangaLongClick = { actionManga = it },
                        onPosterVisible = { model.loadPosterAdaptations(listOf(it)) },
                        onContinueReading = { entry ->
                            nav.navigate("reader/${Uri.encode(entry.mangaId)}/${Uri.encode(entry.chapterId)}")
                        },
                    ) }
                    if (route == "library") LibraryScreen(
                        updates = libraryUpdates,
                        onRead = { update -> nav.navigate("reader/${Uri.encode(update.manga.id)}/${Uri.encode(update.chapter.id)}") },
                        copy = LibraryCopy(
                            series = stringResource(R.string.library_series),
                            updates = stringResource(R.string.library_updates),
                            read = stringResource(R.string.library_read),
                            chapter = stringResource(R.string.library_chapter),
                            emptyTitle = stringResource(R.string.library_updates_empty_title),
                            emptyMessage = stringResource(R.string.library_updates_empty_message),
                            refresh = stringResource(R.string.library_refresh),
                        ),
                        onRefresh = model::refreshLibraryUpdates,
                        refreshing = libraryRefreshing,
                        refreshError = libraryRefreshError,
                        series = seriesContent,
                    ) else seriesContent()
                }
            }
            composable("vault") {
                BackHandler { panic() }
                if (!vaultEnabled) {
                    // The tab is gone; a stale back-stack entry must not reveal the Vault.
                    LaunchedEffect(Unit) { home() }
                } else {
                    // v8.2.0 first-use NSFW consent: a separate, one-time gate that
                    // follows the biometric/PIN unlock. Until it is answered the Vault
                    // stays unlocked-but-empty; either button persists the decision.
                    val vaultNsfwConsent = settings.vaultNsfwConsent
                    if (unlocked && vaultNsfwConsent == null) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text(stringResource(R.string.vault_consent_title)) },
                            text = { Text(stringResource(R.string.vault_consent_body)) },
                            confirmButton = { TextButton(onClick = { onVaultNsfwConsent(true) }) {
                                Text(stringResource(R.string.vault_consent_show))
                            } },
                            dismissButton = { TextButton(onClick = { onVaultNsfwConsent(false) }) {
                                Text(stringResource(R.string.vault_consent_hide))
                            } },
                        )
                    }
                    val adultContentVisible = vaultNsfwConsent == true
                    VaultScreen(
                        unlocked = unlocked,
                        manga = if (unlocked && adultContentVisible) catalog.manga else emptyList(),
                        columns = vaultColumns,
                        onColumns = { model.setColumns(it, true) },
                        onUnlock = onUnlockVault,
                        onPanic = ::panic,
                        onManga = ::details,
                        onMangaLongClick = { actionManga = it },
                        authError = authError,
                        loading = catalog.loading,
                        error = catalog.error,
                        onRetry = model::refresh,
                        discovered = if (adultContentVisible) vaultDiscovery.items else emptyList(),
                        loadingMore = vaultDiscovery.loading,
                        hasMore = vaultDiscovery.hasMore,
                        pagingError = vaultDiscovery.error,
                        onLoadMore = { if (unlocked && adultContentVisible) model.loadNextPage(adult = true) },
                        copy = vaultCopy(catalogCopy),
                        excludedTags = preferences.excludedTags,
                        isAdult = preferences.isAdult,
                        allowAdultContent = preferences.allowAdultContent,
                        showAdultContent = adultContentVisible,
                        diagnosticInput = if (unlocked && adultContentVisible && maintenanceState.readerPreferences != null) rawCatalog.manga + rawVaultDiscovery.items else emptyList(),
                        adaptationBadges = if (unlocked) adaptationBadges else emptyMap(),
                        onPosterVisible = { if (unlocked && settings.vaultVisible) model.loadPosterAdaptations(listOf(it)) },
                    )
                }
            }
            composable("profile") {
                val profile by journeyModel.profileState.collectAsStateWithLifecycle()
                ProfileJourneyScreen(
                    state = profile,
                    onOpenWrapped = { nav.navigate("wrapped") { launchSingleTop = true } },
                    onRefresh = journeyModel::refresh,
                    copy = journeyCopy(),
                )
            }
            composable("wrapped") {
                BackHandler { nav.popBackStack() }
                val wrapped by journeyModel.wrapped.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { journeyModel.loadWrapped() }
                WrappedScreen(
                    state = wrapped,
                    onClose = { nav.popBackStack() },
                    onPeriodChange = { period -> journeyModel.loadWrapped(period) },
                    copy = wrappedCopy(),
                )
            }
            composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val manga = catalog.manga.firstOrNull { it.id == id }
                    ?: discovery.items.firstOrNull { it.id == id }
                    ?: vaultDiscovery.items.firstOrNull { it.id == id }
                if (manga == null) {
                    Column { CatalogEmptyState(stringResource(R.string.detail_not_found_title), stringResource(R.string.detail_not_found_message)); TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.action_back)) } }
                } else if (manga.contentRating != ContentRating.EXPLICIT || unlocked) {
                    val chapterFlow = remember(id) { model.chapters(id) }
                    val chapters by chapterFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    val connections by model.connections.collectAsStateWithLifecycle()
                    LaunchedEffect(id) {
                        model.loadConnections(manga)
                        model.ensureSource(manga)
                    }
                    MangaDetailScreen(manga, chapters, matching, id in saved,
                        onBack = { model.clearMatch(); nav.popBackStack() }, onSave = { model.toggleSaved(id) },
                        onRead = { chapter -> nav.navigate("reader/${Uri.encode(manga.id)}/${Uri.encode(chapter.id)}") },
                        onContinue = { fallback -> model.resumeChapter(manga.id, fallback.id) { chapterId ->
                            // An asynchronous Room result must not reopen a screen the user left.
                            if (nav.currentBackStackEntry == entry && (manga.contentRating != ContentRating.EXPLICIT || model.unlocked.value)) {
                                nav.navigate("reader/${Uri.encode(manga.id)}/${Uri.encode(chapterId)}")
                            }
                        } },
                        onToggleRead = { model.toggleRead(it.id) },
                        onMarkReadThrough = model::markReadThrough,
                        onPanic = if (manga.contentRating == ContentRating.EXPLICIT) ::panic else null,
                        // Connection cards have no safety tags: only show metadata we can verify.
                        related = connections.related.filter { relation -> catalog.manga.any { it.id == relation.id && allowed(it) } },
                        recommended = connections.recommended.filter { recommendation -> catalog.manga.any { it.id == recommendation.id && allowed(it) } },
                        adaptations = connections.adaptations.filter { preferences.excludedTags.isEmpty() &&
                            (it.kind != com.yomira.core.model.AdaptationKind.HENTAI_OVA || vaultEnabled && unlocked) },
                        connectionsLoading = connections.loading,
                        onOpenSeries = { seriesId, seriesTitle ->
                            model.openSeries(seriesId, seriesTitle) { targetId ->
                                // An asynchronous import must not reopen a screen the user left.
                                if (nav.currentBackStackEntry == entry) {
                                    if (targetId == null) scope.launch { snackbar.showSnackbar(openSeriesFailed) }
                                    else {
                                        model.clearMatch()
                                        nav.navigate("details/${Uri.encode(targetId)}")
                                    }
                                }
                            }
                        },
                        copy = detailCopy(catalogCopy),
                        twoPane = twoPaneDetail,
                    )
                }
            }
            composable("reader/{mangaId}/{chapterId}", arguments = listOf(
                navArgument("mangaId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType },
            )) { entry ->
                val mangaId = entry.arguments?.getString("mangaId") ?: return@composable
                val chapterId = entry.arguments?.getString("chapterId") ?: return@composable
                val manga = catalog.manga.firstOrNull { it.id == mangaId }
                if (manga != null && (manga.contentRating != ContentRating.EXPLICIT || unlocked)) {
                    var resolvedId by remember(mangaId, chapterId) { mutableStateOf<String?>(null) }
                    var resolving by remember(mangaId, chapterId) { mutableStateOf(true) }
                    var retryAttempt by remember(mangaId, chapterId) { mutableStateOf(0) }
                    LaunchedEffect(mangaId, chapterId, retryAttempt) {
                        resolving = true
                        resolvedId = null
                        try { resolvedId = model.resolveReadingChapter(manga, chapterId) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { resolvedId = null }
                        finally { resolving = false }
                    }
                    if (resolving) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else if (resolvedId == null) Column {
                        Text(stringResource(R.string.match_unreachable))
                        TextButton(onClick = { resolving = true; retryAttempt++ }) { Text(stringResource(R.string.reader_retry)) }
                        TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.action_back)) }
                    }
                    else ReaderScreen(mangaId, resolvedId!!, readerModel, onBack = { nav.popBackStack() }, defaultMode = settings.readingMode,
                        defaultGuidedView = settings.guidedView, copy = readerCopy(),
                        onChapter = { target ->
                            nav.navigate("reader/${Uri.encode(mangaId)}/${Uri.encode(target)}") {
                                popUpTo("reader/{mangaId}/{chapterId}") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                } else if (!catalog.loading && manga == null) {
                    Column { Text(stringResource(R.string.detail_not_found_title)); TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.action_back)) } }
                }
            }
            composable("settings") {
                val maintenance by maintenanceModel.state.collectAsStateWithLifecycle()
                val driveSignIn = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    maintenanceModel.completeDriveSignIn(result.data,
                        cancelled = result.resultCode == android.app.Activity.RESULT_CANCELED && result.data == null)
                }
                LaunchedEffect(Unit) { maintenanceModel.refresh(); settingsModel.refreshRepositories() }
                SettingsScreen(
                    state = settingsState,
                    currentLanguage = AppLanguage.fromTag(LocaleManagerCompat.currentApplicationTag()),
                    versionName = BuildConfig.VERSION_NAME,
                    onBack = { nav.popBackStack() },
                    // Hiding is instant; revealing must pass the device-credential gate first.
                    onVaultVisibilityChange = { visible -> if (visible) onRevealVault() else settingsModel.setVaultVisible(false) },
                    onVaultNsfwConsentChange = settingsModel::setVaultNsfwConsent,
                    onRequireDeviceLockChange = settingsModel::setRequireDeviceLock,
                    onStealthShieldChange = settingsModel::setStealthShield,
                    onLockOnBackgroundChange = settingsModel::setLockOnBackground,
                    onLanguageChange = { language -> LocaleManagerCompat.setApplicationLocales(language.tag) },
                    onReadingModeChange = settingsModel::setReadingMode,
                    onGuidedViewChange = settingsModel::setGuidedView,
                    onRepositoryToggle = settingsModel::setRepositoryEnabled,
                    onAddCustomRepository = settingsModel::addCustomRepository,
                    onRemoveRepository = settingsModel::removeRepository,
                    onClearCache = maintenanceModel::clearCache,
                    onStartMetadataBackfill = maintenanceModel::startMetadataBackfill,
                    onCancelMetadataBackfill = maintenanceModel::cancelMetadataBackfill,
                    backupActions = { com.yomira.app.backup.LocalBackupActions(backupModel, enabled = !maintenance.resetBusy) },
                    onFactoryReset = maintenanceModel::factoryReset,
                    onCheckUpdates = settingsModel::checkUpdates,
                    onOpenLink = { openReference(it) },
                    copy = settingsCopy(),
                    maintenance = maintenance,
                    onAutomaticSyncChange = maintenanceModel::setAutomaticSync,
                    onSyncNow = maintenanceModel::syncNow,
                    onConnectDrive = {
                        maintenanceModel.beginDriveSignIn()?.let { intent ->
                            try { driveSignIn.launch(intent) }
                            catch (_: Exception) { maintenanceModel.driveSignInLaunchFailed() }
                        }
                    },
                    onDisconnectDrive = maintenanceModel::disconnectDrive,
                    onContentLanguageChange = maintenanceModel::setContentLanguage,
                    onConsumeMessage = maintenanceModel::consumeMessage,
                    buildInfo = "${BuildConfig.BUILD_TYPE} (${BuildConfig.VERSION_CODE})",
                    onReaderPreferencesChange = maintenanceModel::setReaderPreferences,
                    onRerunSurvey = { nav.navigate("survey") { launchSingleTop = true } },
                    onOpenExtensions = { nav.navigate("extensions") { launchSingleTop = true } },
                    extensionsLabel = stringResource(R.string.extensions_title),
                )
            }
            composable("extensions") {
                val extensionState by extensionsModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { extensionsModel.load() }
                ExtensionsScreen(
                    state = extensionState, onBack = { nav.popBackStack() }, onRefresh = extensionsModel::refresh,
                    onQuery = extensionsModel::setQuery, onFilter = extensionsModel::setFilter,
                    onLanguage = extensionsModel::setLanguage, onAddRepository = extensionsModel::addRepository,
                    onRemoveRepository = extensionsModel::removeRepository, onAction = extensionsModel::act,
                )
            }
            composable("survey") {
                var submitted by rememberSaveable { mutableStateOf(false) }
                OnboardingScreen(
                    saving = maintenanceState.preferenceBusy,
                    error = if (maintenanceState.message == MaintenanceMessage.PREFERENCE_FAILED) stringResource(R.string.onboarding_failed_save) else null,
                    copy = onboardingCopy(),
                    initialLanguage = preferences.language.code,
                    initialIsAdult = preferences.isAdult,
                    initialExcludedTags = preferences.excludedTags,
                    initialFavoriteGenres = preferences.favoriteGenres,
                    showRepositoryStep = false,
                    initialFavoriteSeries = preferences.favoriteSeries,
                    catalog = model.catalog.collectAsStateWithLifecycle().value.manga,
                    onLanguageSelected = { LocaleManagerCompat.setApplicationLocales(it) },
                    onComplete = { language, adult, excluded, favorites, series ->
                        submitted = true
                        maintenanceModel.setReaderPreferences(ReaderPreferences(
                            ReadingLanguage.entries.first { it.code == language }, adult, adult, excluded, favorites, series,
                        ))
                        LocaleManagerCompat.setApplicationLocales(language)
                    },
                )
                LaunchedEffect(maintenanceState.readerPreferences, maintenanceState.preferenceBusy) {
                    if (submitted && !maintenanceState.preferenceBusy && maintenanceState.message != MaintenanceMessage.PREFERENCE_FAILED) nav.popBackStack()
                }
            }
            }
        }
    }
    }
}

/** Compact EN/TR toggle; switching recreates the activity in the new locale instantly. */
@Composable
private fun LanguageSwitchButton(onToggle: () -> Unit) {
    val target = if (LocaleManagerCompat.isTurkish()) LocaleManagerCompat.ENGLISH else LocaleManagerCompat.TURKISH
    val description = stringResource(R.string.language_toggle_description)
    TextButton(onClick = onToggle, modifier = Modifier.semantics {
        contentDescription = "$description → $target"
    }) { Text(target.uppercase()) }
}

@Composable
private fun settingsCopy() = SettingsCopy(
    title = stringResource(R.string.settings_title),
    subtitle = stringResource(R.string.settings_subtitle),
    back = stringResource(R.string.action_back),
    storageSection = stringResource(R.string.settings_storage_section),
    backfillTitle = stringResource(R.string.metadata_backfill_title),
    backfillBody = stringResource(R.string.metadata_backfill_body),
    backfillProgress = stringResource(R.string.metadata_backfill_progress),
    cacheSize = stringResource(R.string.settings_cache_size),
    loading = stringResource(R.string.settings_cache_loading),
    confirmClearCache = stringResource(R.string.settings_confirm_cache),
    factoryReset = stringResource(R.string.settings_factory_reset),
    factoryResetBody = stringResource(R.string.settings_factory_reset_body),
    factoryResetWarning = stringResource(R.string.settings_factory_reset_warning),
    confirmFactoryReset = stringResource(R.string.settings_confirm_factory_reset),
    reset = stringResource(R.string.settings_reset),
    resetting = stringResource(R.string.settings_resetting),
    resetFailed = stringResource(R.string.settings_reset_failed),
    syncSection = stringResource(R.string.settings_sync_section),
    connectDrive = stringResource(R.string.settings_connect_drive),
    connectDriveBody = stringResource(R.string.settings_connect_drive_body),
    disconnectDrive = stringResource(R.string.settings_disconnect_drive),
    driveConnected = stringResource(R.string.settings_drive_connected),
    driveSyncActive = stringResource(R.string.settings_drive_sync_active),
    driveSyncManual = stringResource(R.string.settings_drive_sync_manual),
    authFailed = stringResource(R.string.settings_auth_failed),
    disconnectFailed = stringResource(R.string.settings_disconnect_failed),
    automaticSync = stringResource(R.string.settings_automatic_sync),
    automaticSyncBody = stringResource(R.string.settings_automatic_sync_body),
    syncSuggestion = stringResource(R.string.settings_sync_suggestion),
    syncNow = stringResource(R.string.settings_sync_now),
    syncing = stringResource(R.string.settings_syncing),
    lastSynced = stringResource(R.string.settings_last_synced),
    neverSynced = stringResource(R.string.settings_never_synced),
    syncSuccess = stringResource(R.string.settings_sync_success),
    signInRequired = stringResource(R.string.settings_sign_in_required),
    syncFailed = stringResource(R.string.settings_sync_failed),
    cacheSizeFailed = stringResource(R.string.settings_cache_size_failed),
    preferenceFailed = stringResource(R.string.settings_preference_failed),
    contentLanguage = stringResource(R.string.settings_content_language),
    languageAll = stringResource(R.string.settings_language_all),
    contentLanguageBody = stringResource(R.string.settings_content_language_body),
    cacheClearSuccess = stringResource(R.string.settings_cache_clear_success),
    privacySection = stringResource(R.string.settings_privacy_section),
    stealthShieldTitle = stringResource(R.string.settings_stealth_shield_title),
    stealthShieldBody = stringResource(R.string.settings_stealth_shield_body),
    vaultTitle = stringResource(R.string.settings_vault_title),
    vaultBody = stringResource(R.string.settings_vault_body),
    vaultNsfwTitle = stringResource(R.string.settings_vault_nsfw_title),
    vaultNsfwBody = stringResource(R.string.settings_vault_nsfw_body),
    vaultHiddenBadge = stringResource(R.string.settings_vault_hidden_badge),
    revealDenied = stringResource(R.string.settings_reveal_denied),
    deviceLockTitle = stringResource(R.string.settings_device_lock_title),
    deviceLockBody = stringResource(R.string.settings_device_lock_body),
    lockBackgroundTitle = stringResource(R.string.settings_lock_background_title),
    lockBackgroundBody = stringResource(R.string.settings_lock_background_body),
    repositoriesSection = stringResource(R.string.settings_repositories_section),
    repositoriesSectionBody = stringResource(R.string.settings_repositories_section_body),
    addRepository = stringResource(R.string.settings_add_repository),
    addRepositoryTitle = stringResource(R.string.settings_add_repository_title),
    addRepositoryBody = stringResource(R.string.settings_add_repository_body),
    addRepositoryHint = stringResource(R.string.settings_add_repository_hint),
    add = stringResource(R.string.settings_add),
    cancel = stringResource(R.string.settings_cancel),
    removeRepository = stringResource(R.string.settings_remove_repository),
    invalidRepositoryUrl = stringResource(R.string.settings_invalid_repository_url),
    repositoryError = stringResource(R.string.settings_repository_error),
    customRepositories = stringResource(R.string.settings_custom_repositories),
    readingSection = stringResource(R.string.settings_reading_section),
    languageTitle = stringResource(R.string.settings_language_title),
    languageTurkish = stringResource(R.string.settings_language_turkish),
    languageEnglish = stringResource(R.string.settings_language_english),
    readingModeTitle = stringResource(R.string.settings_reading_mode_title),
    modeWebtoon = stringResource(R.string.reader_mode_webtoon),
    modeManga = stringResource(R.string.reader_mode_manga),
    modeWestern = stringResource(R.string.reader_mode_western),
    guidedViewTitle = stringResource(R.string.settings_guided_view_title),
    guidedViewBody = stringResource(R.string.settings_guided_view_body),
    appSection = stringResource(R.string.settings_app_section),
    clearCache = stringResource(R.string.settings_clear_cache),
    clearCacheBody = stringResource(R.string.settings_clear_cache_body),
    cacheCleared = stringResource(R.string.settings_cache_cleared),
    cacheFailed = stringResource(R.string.settings_cache_failed),
    checkUpdates = stringResource(R.string.settings_check_updates),
    updateChecking = stringResource(R.string.settings_update_checking),
    updateUpToDate = stringResource(R.string.settings_update_up_to_date),
    updateAvailable = stringResource(R.string.settings_update_available),
    updateDownload = stringResource(R.string.settings_update_download),
    versionTitle = stringResource(R.string.settings_version_title),
    githubTitle = stringResource(R.string.settings_github_title),
    githubBody = stringResource(R.string.settings_github_body),
)

/** Localized profile/journey copy; the module defaults are Turkish. */
@Composable
private fun journeyCopy() = if (LocaleManagerCompat.isTurkish()) JourneyCopy() else JourneyCopy.english()

/** Localized Wrapped copy for the cinematic recap and its share card. */
@Composable
private fun wrappedCopy() = if (LocaleManagerCompat.isTurkish()) WrappedCopy() else WrappedCopy.english()

@Composable
private fun catalogCopy() = CatalogCopy(
    readNow = stringResource(R.string.catalog_read_now),
    matureBadgeLong = stringResource(R.string.catalog_mature_long),
    matureBadgeShort = stringResource(R.string.catalog_mature_short),
    noCoverArtwork = stringResource(R.string.catalog_no_cover),
    scoreDescription = stringResource(R.string.catalog_score),
    formatManga = stringResource(R.string.catalog_format_manga),
    formatWebtoon = stringResource(R.string.catalog_format_webtoon),
    formatManhua = stringResource(R.string.catalog_format_manhua),
    formatWestern = stringResource(R.string.catalog_format_western),
    columnDescriptions = listOf(
        stringResource(R.string.home_columns_2),
        stringResource(R.string.home_columns_3),
        stringResource(R.string.home_columns_4),
    ),
    navHome = stringResource(R.string.nav_home),
    navDiscover = stringResource(R.string.nav_discover),
    navLibrary = stringResource(R.string.nav_library),
    navProfile = stringResource(R.string.nav_profile),
    navVault = stringResource(R.string.nav_vault),
)

@Composable
private fun homeCopy(catalog: CatalogCopy) = HomeCopy(
    searchPlaceholder = stringResource(R.string.home_search_placeholder),
    subtitle = stringResource(R.string.home_subtitle),
    yourShelf = stringResource(R.string.home_your_shelf),
    retry = stringResource(R.string.home_retry),
    railTrending = stringResource(R.string.home_rail_trending),
    railUpdated = stringResource(R.string.home_rail_updated),
    railPopular = stringResource(R.string.home_rail_popular),
    railPersonal = stringResource(R.string.home_rail_personal),
    railIsekai = stringResource(R.string.home_rail_isekai),
    railEditors = stringResource(R.string.home_rail_editors),
    emptyShelfTitle = stringResource(R.string.home_empty_title),
    emptyShelfMessage = stringResource(R.string.home_empty_message),
    emptySearchTitle = stringResource(R.string.search_empty_title),
    emptySearchMessage = stringResource(R.string.search_empty_message),
    tagsHeading = stringResource(R.string.home_tags_heading),
    clearTags = stringResource(R.string.home_tags_clear),
    pagingLoading = stringResource(R.string.home_paging_loading),
    pagingEnd = stringResource(R.string.home_paging_end),
    continueReading = stringResource(R.string.home_continue_reading),
    continueAction = stringResource(R.string.home_continue_action),
    continueProgress = stringResource(R.string.home_continue_progress),
    dismissContinueReading = stringResource(R.string.control_dismiss_resume),
    resetProgress = stringResource(R.string.control_reset_progress),
    catalog = catalog,
    shelfTitles = com.yomira.core.model.HomeShelf.entries.zip(
        androidx.compose.ui.res.stringArrayResource(R.array.home_shelf_titles).toList(),
    ).toMap(),
    emptyRail = stringResource(R.string.home_shelf_empty),
    unreadChapters = stringResource(R.string.library_unread_chapters),
)

@Composable
private fun vaultCopy(catalog: CatalogCopy) = VaultCopy(
    heading = stringResource(R.string.vault_heading),
    lockedTitle = stringResource(R.string.vault_locked_title),
    lockedBody = stringResource(R.string.vault_locked_body),
    lockedHint = stringResource(R.string.vault_locked_hint),
    unlockButton = stringResource(R.string.vault_unlock_button),
    backHome = stringResource(R.string.vault_back_home),
    lockFast = stringResource(R.string.vault_lock_fast),
    retry = stringResource(R.string.vault_retry),
    filterAll = stringResource(R.string.vault_filter_all),
    collectionTitle = stringResource(R.string.vault_collection_title),
    emptyTitle = stringResource(R.string.vault_empty_title),
    emptyMessage = stringResource(R.string.vault_empty_message),
    hiddenTitle = stringResource(R.string.vault_hidden_title),
    hiddenMessage = stringResource(R.string.vault_hidden_body),
    pagingLoading = stringResource(R.string.vault_paging_loading),
    pagingEnd = stringResource(R.string.vault_paging_end),
    railTrendingWebtoons = stringResource(R.string.vault_rail_trending_webtoons),
    railHentaiManga = stringResource(R.string.vault_rail_hentai_manga),
    railNewDoujinshi = stringResource(R.string.vault_rail_new_doujinshi),
    railWestern3D = stringResource(R.string.vault_rail_western_3d),
    railPopular = stringResource(R.string.vault_rail_popular),
    emptyRail = stringResource(R.string.vault_rail_empty),
    catalog = catalog,
)

@Composable
private fun detailCopy(catalog: CatalogCopy) = DetailCopy(
    back = stringResource(R.string.detail_back),
    lockFast = stringResource(R.string.detail_lock_fast),
    statusOngoing = stringResource(R.string.detail_status_ongoing),
    statusCompleted = stringResource(R.string.detail_status_completed),
    statusHiatus = stringResource(R.string.detail_status_hiatus),
    statusCancelled = stringResource(R.string.detail_status_cancelled),
    statusUnknown = stringResource(R.string.detail_status_unknown),
    saveAdd = stringResource(R.string.detail_save_add),
    saveRemove = stringResource(R.string.detail_save_remove),
    authorLine = stringResource(R.string.detail_author),
    artistLine = stringResource(R.string.detail_artist),
    notSpecified = stringResource(R.string.detail_not_specified),
    synopsisMissing = stringResource(R.string.detail_synopsis_missing),
    summaryCollapse = stringResource(R.string.detail_summary_collapse),
    summaryExpand = stringResource(R.string.detail_summary_expand),
    readNow = stringResource(R.string.detail_read_now),
    readStart = stringResource(R.string.detail_read_start),
    readContinue = stringResource(R.string.detail_read_continue),
    chaptersHeading = stringResource(R.string.detail_chapters_heading),
    sortNewest = stringResource(R.string.detail_sort_newest),
    sortOldest = stringResource(R.string.detail_sort_oldest),
    chaptersEmptyTitle = stringResource(R.string.detail_chapters_empty_title),
    chaptersEmptyMessage = stringResource(R.string.detail_chapters_empty_message),
    chaptersLoading = stringResource(R.string.detail_chapters_loading),
    chapterFallback = stringResource(R.string.detail_chapter_fallback),
    dateMissing = stringResource(R.string.detail_date_missing),
    markRead = stringResource(R.string.detail_mark_read),
    markReadThrough = stringResource(R.string.control_mark_read_through),
    markUnread = stringResource(R.string.detail_mark_unread),
    readLabel = stringResource(R.string.detail_read_label),
    unreadOnly = stringResource(R.string.detail_unread_only),
    unreadEmptyTitle = stringResource(R.string.detail_unread_empty_title),
    unreadEmptyMessage = stringResource(R.string.detail_unread_empty_message),
    relatedHeading = stringResource(R.string.detail_related_heading),
    relatedSubtitle = stringResource(R.string.detail_related_subtitle),
    recommendedHeading = stringResource(R.string.detail_recommended_heading),
    recommendedSubtitle = stringResource(R.string.detail_recommended_subtitle),
    relationPrequel = stringResource(R.string.detail_relation_prequel),
    relationSequel = stringResource(R.string.detail_relation_sequel),
    relationSpinOff = stringResource(R.string.detail_relation_spin_off),
    relationSideStory = stringResource(R.string.detail_relation_side_story),
    relationAdaptation = stringResource(R.string.detail_relation_adaptation),
    relationAlternative = stringResource(R.string.detail_relation_alternative),
    relationSource = stringResource(R.string.detail_relation_source),
    relationParent = stringResource(R.string.detail_relation_parent),
    relationCharacter = stringResource(R.string.detail_relation_character),
    relationOther = stringResource(R.string.detail_relation_other),
    adaptationAnime = stringResource(R.string.detail_adaptation_anime),
    adaptationHentaiOva = stringResource(R.string.detail_adaptation_hentai_ova),
    adaptationLiveAction = stringResource(R.string.detail_adaptation_live_action),
    adaptationDonghua = stringResource(R.string.detail_adaptation_donghua),
    adaptationSpecial = stringResource(R.string.detail_adaptation_special),
    adaptationEpisodes = stringResource(R.string.detail_adaptation_episodes),
    catalog = catalog,
)

@Composable
private fun readerCopy() = ReaderCopy(
    retry = stringResource(R.string.reader_retry),
    back = stringResource(R.string.reader_back),
    backShort = stringResource(R.string.reader_back_short),
    saveRetry = stringResource(R.string.reader_save_retry),
    pageOf = stringResource(R.string.reader_page_of),
    pageShort = stringResource(R.string.reader_page_short),
    pageDescription = stringResource(R.string.reader_page_description),
    pageLoadFailed = stringResource(R.string.reader_page_failed),
    modeWebtoon = stringResource(R.string.reader_mode_webtoon),
    modeManga = stringResource(R.string.reader_mode_manga),
    modeWestern = stringResource(R.string.reader_mode_western),
    oledBlack = stringResource(R.string.reader_oled_black),
    invertedColors = stringResource(R.string.reader_inverted_colors),
    guidedView = stringResource(R.string.reader_guided_view),
    chapterPicker = stringResource(R.string.reader_chapter_picker),
    nextChapter = stringResource(R.string.reader_next_chapter),
    recapOpen = stringResource(R.string.reader_recap_open),
    recapTitle = stringResource(R.string.reader_recap_title),
    recapCharacters = stringResource(R.string.reader_recap_characters),
    recapResume = stringResource(R.string.reader_recap_resume),
    recapWithheld = stringResource(R.string.reader_recap_withheld),
    recapEmpty = stringResource(R.string.reader_recap_empty),
    recapClose = stringResource(R.string.reader_recap_close),
)

@Composable
private fun updateDialogCopy() = UpdateDialogCopy(
    title = stringResource(R.string.update_title),
    changesLabel = stringResource(R.string.update_changes),
    apkNote = stringResource(R.string.update_apk_note),
    updateNow = stringResource(R.string.update_now),
    later = stringResource(R.string.update_later),
    downloading = stringResource(R.string.update_downloading),
    install = stringResource(R.string.update_install),
    failed = stringResource(R.string.update_download_failed),
)

@Composable
private fun onboardingCopy() = OnboardingCopy(
    languageTitle = stringResource(R.string.onboarding_language_title),
    languageDescription = stringResource(R.string.onboarding_language_description),
    languageTurkish = stringResource(R.string.onboarding_language_turkish),
    languageEnglish = stringResource(R.string.onboarding_language_english),
    languageBoth = stringResource(R.string.onboarding_language_both),
    languageNote = stringResource(R.string.onboarding_language_note),
    saving = stringResource(R.string.onboarding_saving),
    connectAndStart = stringResource(R.string.onboarding_connect_start),
    ageTitle = stringResource(R.string.onboarding_age_title),
    ageDescription = stringResource(R.string.onboarding_age_description),
    exclusionsTitle = stringResource(R.string.onboarding_exclusions_title),
    exclusionsDescription = stringResource(R.string.onboarding_exclusions_description),
    genresTitle = stringResource(R.string.onboarding_genres_title),
    genresDescription = stringResource(R.string.onboarding_genres_description),
    seriesSearch = stringResource(R.string.onboarding_series_search),
    seriesEmpty = stringResource(R.string.onboarding_series_empty),
    removeFavorite = stringResource(R.string.onboarding_series_remove),
    skip = stringResource(R.string.onboarding_series_skip),
    adult = stringResource(R.string.onboarding_adult),
    minor = stringResource(R.string.onboarding_minor),
    safeMode = stringResource(R.string.onboarding_safe_mode),
    gore = stringResource(R.string.onboarding_gore),
    back = stringResource(R.string.onboarding_back),
    continueAction = stringResource(R.string.onboarding_continue),
    repositoriesTitle = stringResource(R.string.repo_step_title),
    repositoriesDescription = stringResource(R.string.repo_step_body),
    addRepository = stringResource(R.string.repo_add_official),
    openRepository = stringResource(R.string.repo_open_page),
    confirmRepository = stringResource(R.string.repo_confirm_title),
    cancel = stringResource(R.string.settings_cancel),
)
