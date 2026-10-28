package com.yomira.feature.settings

import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.MainDestination
import com.yomira.domain.extension.OnboardingWizardManager
import kotlinx.coroutines.flow.Flow
import java.util.Locale

/**
 * UI language accepted by the settings screen. The framework per-app locale
 * (LocaleManagerCompat) stays the single source of truth; this enum only
 * shapes the segmented control at the boundary.
 */
enum class AppLanguage(val tag: String) {
    TURKISH("tr"), ENGLISH("en");

    companion object {
        fun fromTag(tag: String): AppLanguage = if (tag.startsWith(TURKISH.tag, ignoreCase = true)) TURKISH else ENGLISH
    }
}

/**
 * Device-local application settings. A fresh install keeps private features hidden.
 */
data class AppSettings(
    /** When false the +18 Vault tab is removed from the UI entirely, leaving no trace. */
    val vaultVisible: Boolean = false,
    /** When true entering the Vault requires the device credential (PIN/pattern/biometric). */
    val requireDeviceLock: Boolean = true,
    /** When true the Vault re-locks as soon as the app is backgrounded. */
    val lockOnBackground: Boolean = true,
    /** Reader mode a series starts in before the user picks one explicitly. */
    val readingMode: ReadingMode = ReadingMode.MANGA_RTL,
    /** When true the reader opens in Guided View (panel-by-panel cinematic reading). */
    val guidedView: Boolean = false,
    /**
     * Set once the bundled catalogs have been registered on a fresh install.
     * Kept so a repo the user deliberately disables is never resurrected on relaunch.
     */
    val bundledRepositoriesSeeded: Boolean = false,
    /** Local opt-in; never overwritten by a restored cloud document. */
    val automaticSync: Boolean = false,
    /** Encrypted DNS and best-effort TLS ClientHello fragmentation; device-local. */
    val stealthShieldEnabled: Boolean = true,
    /** Upgrade cleanup revoked bundled defaults; an unset seed flag must not register them again. */
    val repositoriesUserManaged: Boolean = false,
)

data class SettingsMaintenanceState(
    val cacheBytes: Long? = null,
    val cacheSizeFailed: Boolean = false,
    val cacheBusy: Boolean = false,
    val syncBusy: Boolean = false,
    val driveSignInRequired: Boolean = false,
    val driveAccountEmail: String? = null,
    val driveAuthBusy: Boolean = false,
    val lastSyncedAt: Long? = null,
    val contentLanguage: com.yomira.domain.extension.ReadingLanguage? = null,
    val readerPreferences: com.yomira.domain.extension.ReaderPreferences? = null,
    val preferenceBusy: Boolean = false,
    val resetBusy: Boolean = false,
    val message: MaintenanceMessage? = null,
)

enum class MaintenanceMessage { CACHE_CLEARED, CACHE_FAILED, CACHE_SIZE_FAILED, SYNC_SUCCESS, SIGN_IN_REQUIRED, AUTH_FAILED, DISCONNECT_FAILED, SYNC_FAILED, PREFERENCE_FAILED, RESET_FAILED }

/** Reactive, atomically updated settings persistence. Implementations must be safe to call from any thread. */
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun read(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Clears expendable on-device caches; returns the number of bytes freed. */
fun interface AppCacheCleaner {
    suspend fun clear(): Long
}

/** A repository index the settings screen can register or revoke with one switch. */
data class CuratedRepository(
    val id: String,
    val title: String,
    val subtitle: String,
    val indexUrl: String,
    /** Curated indexes ship inside the app and are registered on a fresh install; the switch revokes them. */
    val enabledByDefault: Boolean = true,
)

object CuratedRepositories {
    /** The very URL onboarding registers, so the switch reflects that consent instead of duplicating it. */
    val UNIVERSAL = CuratedRepository(
        id = "keiyoushi",
        title = "Keiyoushi Universal Deposu",
        subtitle = "Tüm dillerde 1000+ topluluk kaynağı; evrensel açık kaynak indeks.",
        indexUrl = OnboardingWizardManager.KEIYOUSHI_INDEX,
    )

    val TURKISH_COMICS = CuratedRepository(
        id = "turkish",
        title = "Türkçe Çizgi Roman & Manga Deposu",
        subtitle = "Manga-TR, MangaDenizi, Hayalistic, Zenith Scans vb. 70+ yerel kaynak.",
        indexUrl = OnboardingWizardManager.TURKISH_INDEX,
    )

    val WESTERN_COMICS = CuratedRepository(
        id = "western",
        title = "Batı Çizgi Romanları Deposu",
        subtitle = "Marvel/DC tarzı çizgi roman ve western webcomic kaynakları.",
        indexUrl = OnboardingWizardManager.WESTERN_INDEX,
    )

    val ALL = listOf(UNIVERSAL, TURKISH_COMICS, WESTERN_COMICS)
}

const val YOMIRA_GITHUB_URL = "https://github.com/PaelenRune/Yomira"

/**
 * Bottom-bar destinations for the current privacy state — the exact filter
 * [YomiraBottomNavBar][com.yomira.core.ui.YomiraBottomNavBar] renders. Hiding
 * the Vault removes the tab completely (not merely locking it), so nobody
 * holding the phone can tell adult content exists at all.
 */
fun visibleBottomDestinations(vaultVisible: Boolean): List<MainDestination> =
    MainDestination.entries.filter { vaultVisible || it != MainDestination.VAULT }

/** Registered URLs that are not one of the curated indexes shown with their own switch. */
fun customRepositories(registered: List<String>): List<String> {
    val curated = CuratedRepositories.ALL.map { it.indexUrl }.toSet()
    return registered.filterNot { it in curated }
}

/** Human-readable byte count for cache feedback; pinned to Locale.ROOT so tests are deterministic. */
fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1_024 * 1_024 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1_024.0)
    else -> "$bytes B"
}

/** One-shot outcome of the manual update check driven by AppUpdateChecker. */
sealed interface UpdateCheckStatus {
    data object Idle : UpdateCheckStatus
    data object Checking : UpdateCheckStatus
    data object UpToDate : UpdateCheckStatus
    data class Available(val version: String, val downloadUrl: String, val releaseNotes: String) : UpdateCheckStatus
}

/**
 * Everything the settings screen renders. Flags carry facts only; the composable
 * maps them to localized copy so the view model stays language-agnostic.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val repositories: List<String> = emptyList(),
    val busyRepository: String? = null,
    val updateCheck: UpdateCheckStatus = UpdateCheckStatus.Idle,
    val cacheClearedBytes: Long? = null,
    val cacheFailed: Boolean = false,
    val repositoryError: Boolean = false,
    val invalidRepositoryUrl: Boolean = false,
    val revealDenied: Boolean = false,
)
