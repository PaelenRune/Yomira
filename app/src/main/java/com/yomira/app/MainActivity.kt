package com.yomira.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.yomira.app.onboarding.OnboardingViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.ui.*
import com.yomira.core.ui.theme.YomiraTheme
import com.yomira.feature.home.*
import com.yomira.feature.vault.VaultScreen
import com.yomira.feature.details.MangaDetailScreen
import com.yomira.feature.reader.ReaderScreen
import com.yomira.feature.reader.ReaderViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: YomiraViewModel by viewModels()
    private val readerModel: ReaderViewModel by viewModels()
    private val onboardingModel: OnboardingViewModel by viewModels()
    private var authError by mutableStateOf<String?>(null)
    private var confirmingCredential = false
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        confirmingCredential = false
        model.unlocked.value = result.resultCode == Activity.RESULT_OK
        authError = if (result.resultCode == Activity.RESULT_OK) null else "Doğrulama tamamlanmadı. Yeniden deneyebilirsin."
    }

    @Suppress("DEPRECATION")
    private fun unlock() {
        if (confirmingCredential) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        val intent = keyguard.createConfirmDeviceCredentialIntent("Yomira Vault", "Özel vitrinin için cihaz kilidini doğrula")
        if (intent == null) { authError = "Önce Android ayarlarından güvenli bir ekran kilidi (PIN, desen veya şifre) oluştur."; return }
        confirmingCredential = true
        authentication.launch(intent)
    }

    override fun onStop() {
        // The credential activity itself must not invalidate an authentication request.
        if (!confirmingCredential) model.lock()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Also hides all app previews in Recents; no sensitive frame can leak during navigation.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { YomiraTheme {
            val onboarding by onboardingModel.state.collectAsStateWithLifecycle()
            when {
                onboarding.loading -> Surface(color = Color(0xFF06050C), modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (onboarding.error == null) CircularProgressIndicator()
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(onboarding.error.orEmpty())
                            TextButton(onClick = onboardingModel::load) { Text("Yeniden dene") }
                        }
                    }
                }
                !onboarding.completed -> OnboardingScreen(onboarding.saving, onboarding.error, onboardingModel::complete)
                else -> YomiraNavigation(model, readerModel, authError, {
                    if (onboarding.adultEnabled) unlock()
                    else authError = "Yetişkin içerik izni kapalı. Vault kullanılamaz."
                }, ::openReference)
            }
        } }
    }

    private fun openReference(url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return false
        return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true } catch (_: android.content.ActivityNotFoundException) { false }
    }
}

@Composable
private fun YomiraNavigation(model: YomiraViewModel, readerModel: ReaderViewModel, authError: String?, onUnlock: () -> Unit, openReference: (String) -> Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val catalog by model.catalog.collectAsStateWithLifecycle()
    val columns by model.columns.collectAsStateWithLifecycle()
    val vaultColumns by model.vaultColumns.collectAsStateWithLifecycle()
    val unlocked by model.unlocked.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val match by model.match.collectAsStateWithLifecycle()
    val matching by model.matching.collectAsStateWithLifecycle()
    val liveSearch by model.liveSearch.collectAsStateWithLifecycle()
    val pendingUpdate by model.update.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val current = backStack?.destination?.route
    fun home() { nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = false }; launchSingleTop = true } }
    fun panic() { model.lock(); home() }
    fun details(manga: Manga) {
        if (manga.contentRating == ContentRating.EXPLICIT && !unlocked) return
        model.clearMatch()
        nav.navigate("details/${Uri.encode(manga.id)}")
    }
    pendingUpdate?.let { info ->
        UpdateDialog(
            version = info.version,
            releaseNotes = info.releaseNotes,
            onUpdate = {
                if (openReference(info.apkDownloadUrl)) model.closeUpdate()
                else scope.launch { snackbar.showSnackbar("Güncelleme bağlantısı açılamadı.") }
            },
            onDismiss = model::dismissUpdate,
        )
    }
    // A locked adult detail is removed, not just covered with a lock overlay.
    LaunchedEffect(unlocked, current, catalog.manga) {
        val id = backStack?.arguments?.getString("id") ?: backStack?.arguments?.getString("mangaId")
        if (!unlocked && (current == "details/{id}" || current == "reader/{mangaId}/{chapterId}") && catalog.manga.any { it.id == id && it.contentRating == ContentRating.EXPLICIT }) panic()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (current != "details/{id}" && current != "reader/{mangaId}/{chapterId}") YomiraBottomNavBar(current) { destination ->
            if (destination != MainDestination.VAULT) model.lock()
            nav.navigate(destination.route) { popUpTo("home") { inclusive = false }; launchSingleTop = true }
        }
    }) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()
            .then(if (current == "reader/{mangaId}/{chapterId}") Modifier else Modifier.padding(padding))) {
            listOf("home", "discover", "library").forEach { route ->
                composable(route) {
                    var query by rememberSaveable { mutableStateOf("") }
                    val items = publicCatalog(catalog.manga).filter { route != "library" || it.id in saved }
                    HomeScreen(
                        HomeUiState(items, items.sortedByDescending { it.score }, publicCatalog(catalog.updated).filter { route != "library" || it.id in saved }, columns, query, catalog.loading, catalog.error, liveSearch.searching, liveSearch.message),
                        onQuery = { value -> query = value; model.onQueryChanged(value) }, onColumns = { model.setColumns(it) }, onManga = ::details, onRetry = model::refresh,
                        title = when (route) { "discover" -> "Keşfet"; "library" -> "Kitaplığın"; else -> "Yomira" }, showHero = route == "home",
                    )
                }
            }
            composable("vault") {
                BackHandler { panic() }
                VaultScreen(unlocked, if (unlocked) catalog.manga else emptyList(), vaultColumns, { model.setColumns(it, true) }, onUnlock, ::panic, ::details, authError, catalog.loading, catalog.error, model::refresh)
            }
            composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val manga = catalog.manga.firstOrNull { it.id == id }
                if (manga == null) {
                    Column { CatalogEmptyState("Seri bulunamadı", "Seri kaldırılmış veya katalog henüz yüklenmemiş olabilir."); TextButton(onClick = { nav.popBackStack() }) { Text("Geri dön") } }
                } else if (manga.contentRating != ContentRating.EXPLICIT || unlocked) {
                    val chapterFlow = remember(id) { model.chapters(id) }
                    val chapters by chapterFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    MangaDetailScreen(manga, chapters, match, matching, id in saved,
                        onBack = { model.clearMatch(); nav.popBackStack() }, onSave = { model.toggleSaved(id) }, onMatch = { model.findSource(manga) },
                        onInstall = { url -> if (!openReference(url)) scope.launch { snackbar.showSnackbar("Güvenli indirme bağlantısı açılamadı.") } },
                        onRead = { chapter -> nav.navigate("reader/${Uri.encode(manga.id)}/${Uri.encode(chapter.id)}") },
                        onContinue = { fallback -> model.resumeChapter(manga.id, fallback.id) { chapterId ->
                            // An asynchronous Room result must not reopen a screen the user left.
                            if (nav.currentBackStackEntry == entry && (manga.contentRating != ContentRating.EXPLICIT || model.unlocked.value)) {
                                nav.navigate("reader/${Uri.encode(manga.id)}/${Uri.encode(chapterId)}")
                            }
                        } },
                        onToggleRead = { model.toggleRead(it.id) },
                        onDownload = { scope.launch { snackbar.showSnackbar("Çevrimdışı indirme için kaynak sayfa sağlayıcısı gerekli; bu sürüm yalnızca bölüm referanslarını saklar.") } },
                        onPanic = if (manga.contentRating == ContentRating.EXPLICIT) ::panic else null,
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
                    ReaderScreen(mangaId, chapterId, readerModel, onBack = { nav.popBackStack() })
                } else if (!catalog.loading && manga == null) {
                    Column { Text("Seri bulunamadı"); TextButton(onClick = { nav.popBackStack() }) { Text("Geri dön") } }
                }
            }
        }
    }
}
