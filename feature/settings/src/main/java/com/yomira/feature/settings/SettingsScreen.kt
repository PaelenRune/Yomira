package com.yomira.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.yomira.domain.extension.ReadingLanguage
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.core.model.blacklistTags
import java.text.DateFormat
import java.util.Date
import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.theme.CardSurface
import com.yomira.core.ui.theme.DeepOledBlack
import com.yomira.core.ui.theme.NeonFuchsia
import com.yomira.core.ui.theme.NeonPurple
import com.yomira.core.ui.theme.TextMuted
import com.yomira.core.ui.theme.TextPrimary

/**
 * The settings surface: privacy & +18 Vault, repository manager, reading and
 * language preferences, and app maintenance. Purely presentational — every
 * mutation flows through the callbacks and lands in [SettingsUiState].
 *
 * The Vault switch is checked while the Vault tab is visible. Turning it off
 * hides the tab immediately (a panic action must never wait for a prompt);
 * turning it back on routes through the caller, which must verify the device
 * credential before [onVaultVisibilityChange] is invoked with `true`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    currentLanguage: AppLanguage,
    versionName: String,
    onBack: () -> Unit,
    onVaultVisibilityChange: (Boolean) -> Unit,
    onRequireDeviceLockChange: (Boolean) -> Unit,
    onLockOnBackgroundChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
    onGuidedViewChange: (Boolean) -> Unit,
    onRepositoryToggle: (CuratedRepository, Boolean) -> Unit,
    onAddCustomRepository: (String) -> Unit,
    onRemoveRepository: (String) -> Unit,
    onClearCache: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenLink: (String) -> Unit,
    copy: SettingsCopy = SettingsCopy(),
    maintenance: SettingsMaintenanceState = SettingsMaintenanceState(),
    onAutomaticSyncChange: (Boolean) -> Unit = {},
    onSyncNow: () -> Unit = {},
    onContentLanguageChange: (ReadingLanguage) -> Unit = {},
    onConsumeMessage: () -> Unit = {},
    buildInfo: String = "",
    onStealthShieldChange: (Boolean) -> Unit = {},
    onReaderPreferencesChange: (ReaderPreferences) -> Unit = {},
    onRerunSurvey: () -> Unit = {},
    onFactoryReset: () -> Unit = {},
    onOpenExtensions: () -> Unit = {},
    extensionsLabel: String = "Eklentiler / Extensions",
    onConnectDrive: () -> Unit = {},
    onDisconnectDrive: () -> Unit = {},
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showCacheDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(maintenance.message) {
        val message = maintenance.message ?: return@LaunchedEffect
        val text = when (message) {
            MaintenanceMessage.CACHE_CLEARED -> copy.cacheClearSuccess
            MaintenanceMessage.CACHE_FAILED -> copy.cacheFailed
            MaintenanceMessage.CACHE_SIZE_FAILED -> copy.cacheSizeFailed
            MaintenanceMessage.SYNC_SUCCESS -> copy.syncSuccess
            MaintenanceMessage.SIGN_IN_REQUIRED -> copy.signInRequired
            MaintenanceMessage.AUTH_FAILED -> copy.authFailed
            MaintenanceMessage.DISCONNECT_FAILED -> copy.disconnectFailed
            MaintenanceMessage.SYNC_FAILED -> copy.syncFailed
            MaintenanceMessage.PREFERENCE_FAILED -> copy.preferenceFailed
            MaintenanceMessage.RESET_FAILED -> copy.resetFailed
        }
        snackbar.showSnackbar(text)
        onConsumeMessage()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D12))) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item { SettingsHeader(copy, onBack) }
            item {
                FilledTonalButton(onClick = onOpenExtensions, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.Extension, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(extensionsLabel)
                }
            }
            item {
                SettingsSection(Icons.Default.FilterAlt, "İçerik Filtreleri & Kara Liste", NeonPurple) {
                    val preferences = maintenance.readerPreferences
                    if (preferences != null) {
                        ChoiceRow("Yaş ve içerik düzeyi") {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("Güvenli Mod", "18+ · Normal", "18+ · Vault").forEachIndexed { index, label ->
                                    val tier = if (!preferences.isAdult) 0 else if (!preferences.allowAdultContent) 1 else 2
                                    SegmentedButton(selected = tier == index,
                                        enabled = !maintenance.preferenceBusy,
                                        onClick = { onReaderPreferencesChange(preferences.copy(isAdult = index > 0, allowAdultContent = index == 2)) },
                                        shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label) }
                                }
                            }
                            Text("Seçili etiketlerdeki seriler her yerde gizlenir.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                blacklistTags.forEach { (key, label) ->
                                    FilterChip(selected = key in preferences.excludedTags, enabled = !maintenance.preferenceBusy,
                                        onClick = { onReaderPreferencesChange(preferences.copy(excludedTags =
                                            if (key in preferences.excludedTags) preferences.excludedTags - key else preferences.excludedTags + key)) },
                                        label = { Text(label) })
                                }
                            }
                            OutlinedButton(onClick = onRerunSurvey, enabled = !maintenance.preferenceBusy) { Text("Okuma Anketini Baştan Yap") }
                        }
                    }
                }
            }
            item {
                SettingsSection(Icons.Default.Storage, copy.storageSection, NeonPurple) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (maintenance.cacheSizeFailed) copy.cacheSizeFailed else
                            copy.cacheSize.format(maintenance.cacheBytes?.let(::formatCacheSize) ?: copy.loading),
                            style = MaterialTheme.typography.titleMedium)
                        Text(copy.clearCacheBody, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Button(onClick = { showCacheDialog = true }, enabled = !maintenance.cacheBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            if (maintenance.cacheBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(copy.clearCache)
                        }
                        HorizontalDivider()
                        Text(copy.factoryResetBody, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Button(
                            onClick = { showResetDialog = true },
                            enabled = !maintenance.resetBusy && !maintenance.cacheBusy && !maintenance.syncBusy && !maintenance.preferenceBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(copy.factoryReset) }
                    }
                }
            }
            item {
                SettingsSection(Icons.Default.CloudSync, copy.syncSection, NeonPurple) {
                    val connected = maintenance.driveAccountEmail != null
                    val available = connected && !maintenance.driveAuthBusy && !maintenance.resetBusy
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (connected) {
                            Text(copy.driveConnected.format(maintenance.driveAccountEmail), style = MaterialTheme.typography.titleMedium)
                            Text(if (maintenance.syncBusy) copy.syncing else if (state.settings.automaticSync)
                                copy.driveSyncActive else copy.driveSyncManual, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            OutlinedButton(onClick = onDisconnectDrive,
                                enabled = available && !maintenance.syncBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(copy.disconnectDrive) }
                        } else {
                            Text(copy.connectDriveBody, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onConnectDrive, enabled = !maintenance.driveAuthBusy && !maintenance.resetBusy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(copy.connectDrive) }
                        }
                        if (maintenance.driveAuthBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    SettingsSwitchRow(copy.automaticSync, copy.automaticSyncBody, state.settings.automaticSync,
                        onAutomaticSyncChange, NeonPurple, enabled = available && !maintenance.syncBusy)
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!state.settings.automaticSync) Text(copy.syncSuggestion, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        if (maintenance.driveSignInRequired) Text(copy.signInRequired,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text(copy.lastSynced.format(maintenance.lastSyncedAt?.let {
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                        } ?: copy.neverSynced), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        OutlinedButton(onClick = onSyncNow, enabled = available && !maintenance.syncBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (maintenance.syncBusy) copy.syncing else copy.syncNow)
                        }
                    }
                }
            }
            item {
                SettingsSection(icon = Icons.Default.Security, title = copy.privacySection, accent = NeonFuchsia) {
                    SettingsSwitchRow(copy.stealthShieldTitle, copy.stealthShieldBody,
                        state.settings.stealthShieldEnabled, onStealthShieldChange, NeonPurple)
                    RowDivider()
                    SettingsSwitchRow(
                        title = copy.vaultTitle,
                        body = copy.vaultBody,
                        checked = state.settings.vaultVisible,
                        onCheckedChange = onVaultVisibilityChange,
                        accent = NeonFuchsia,
                        badge = copy.vaultHiddenBadge.takeIf { !state.settings.vaultVisible },
                        error = copy.revealDenied.takeIf { state.revealDenied },
                    )
                    RowDivider()
                    SettingsSwitchRow(copy.deviceLockTitle, copy.deviceLockBody, state.settings.requireDeviceLock, onRequireDeviceLockChange, NeonPurple)
                    RowDivider()
                    SettingsSwitchRow(copy.lockBackgroundTitle, copy.lockBackgroundBody, state.settings.lockOnBackground, onLockOnBackgroundChange, NeonPurple)
                }
            }
            item {
                SettingsSection(icon = Icons.Default.Extension, title = copy.repositoriesSection, accent = NeonPurple) {
                    Text(copy.repositoriesSectionBody, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    CuratedRepositories.ALL.forEachIndexed { index, repository ->
                        if (index > 0) RowDivider()
                        RepositoryRow(
                            repository = repository,
                            enabled = repository.indexUrl in state.repositories,
                            busy = state.busyRepository == repository.indexUrl,
                            onToggle = { enabled -> onRepositoryToggle(repository, enabled) },
                        )
                    }
                    val custom = customRepositories(state.repositories)
                    if (custom.isNotEmpty()) {
                        RowDivider()
                        Text(copy.customRepositories, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        custom.forEach { url ->
                            CustomRepositoryRow(url = url, removeLabel = copy.removeRepository, onRemove = { onRemoveRepository(url) })
                        }
                    }
                    RowDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = { showAddDialog = true }).padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(20.dp))
                        Text(copy.addRepository, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = NeonPurple)
                    }
                    if (state.invalidRepositoryUrl) {
                        Text(copy.invalidRepositoryUrl, Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (state.repositoryError) {
                        Text(copy.repositoryError, Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                SettingsSection(icon = Icons.Default.Language, title = copy.readingSection, accent = NeonPurple) {
                    ChoiceRow(copy.contentLanguage) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ReadingLanguage.entries.forEachIndexed { index, language ->
                                SegmentedButton(
                                    selected = maintenance.contentLanguage == language,
                                    onClick = { onContentLanguageChange(language) },
                                    enabled = !maintenance.preferenceBusy,
                                    icon = {},
                                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                                ) { Text(when (language) {
                                    ReadingLanguage.TURKISH -> copy.languageTurkish
                                    ReadingLanguage.ENGLISH -> copy.languageEnglish
                                    ReadingLanguage.BOTH -> copy.languageAll
                                }) }
                            }
                        }
                        Text(copy.contentLanguageBody, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    RowDivider()
                    ChoiceRow(copy.languageTitle) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = currentLanguage == AppLanguage.TURKISH,
                                onClick = { onLanguageChange(AppLanguage.TURKISH) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text(copy.languageTurkish) }
                            SegmentedButton(
                                selected = currentLanguage == AppLanguage.ENGLISH,
                                onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text(copy.languageEnglish) }
                        }
                    }
                    RowDivider()
                    ChoiceRow(copy.readingModeTitle) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.settings.readingMode == ReadingMode.WEBTOON,
                                onClick = { onReadingModeChange(ReadingMode.WEBTOON) },
                                shape = SegmentedButtonDefaults.itemShape(0, 3),
                            ) { Text(copy.modeWebtoon) }
                            SegmentedButton(
                                selected = state.settings.readingMode == ReadingMode.MANGA_RTL,
                                onClick = { onReadingModeChange(ReadingMode.MANGA_RTL) },
                                shape = SegmentedButtonDefaults.itemShape(1, 3),
                            ) { Text(copy.modeManga) }
                            SegmentedButton(
                                selected = state.settings.readingMode == ReadingMode.WESTERN_LTR,
                                onClick = { onReadingModeChange(ReadingMode.WESTERN_LTR) },
                                shape = SegmentedButtonDefaults.itemShape(2, 3),
                            ) { Text(copy.modeWestern) }
                        }
                    }
                    RowDivider()
                    SettingsSwitchRow(copy.guidedViewTitle, copy.guidedViewBody, state.settings.guidedView, onGuidedViewChange, NeonPurple)
                }
            }
            item {
                SettingsSection(icon = Icons.Default.Info, title = copy.appSection, accent = NeonPurple) {
                    UpdateRow(copy, state, versionName, onCheckUpdates, onOpenLink)
                    RowDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(copy.versionTitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Yomira v$versionName Beta", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                            if (buildInfo.isNotBlank()) Text(buildInfo, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                    RowDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = { onOpenLink(YOMIRA_GITHUB_URL) }).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(copy.githubTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(copy.githubBody, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (maintenance.resetBusy) AlertDialog(
        onDismissRequest = {},
        title = { Text(copy.resetting) },
        text = { CircularProgressIndicator() },
        confirmButton = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
    if (showResetDialog) AlertDialog(
        onDismissRequest = { showResetDialog = false },
        title = { Text(copy.confirmFactoryReset) },
        text = { Text(copy.factoryResetBody + "\n\n" + copy.factoryResetWarning) },
        confirmButton = {
            TextButton(onClick = { showResetDialog = false; onFactoryReset() }) {
                Text(copy.reset, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(copy.cancel) } },
    )
    if (showCacheDialog) AlertDialog(
        onDismissRequest = { showCacheDialog = false },
        title = { Text(copy.confirmClearCache) },
        text = { Text(copy.clearCacheBody) },
        confirmButton = { TextButton(onClick = { showCacheDialog = false; onClearCache() }) { Text(copy.clearCache) } },
        dismissButton = { TextButton(onClick = { showCacheDialog = false }) { Text(copy.cancel) } },
    )
    if (showAddDialog) {
        AddRepositoryDialog(
            copy,
            onAdd = { url -> onAddCustomRepository(url); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun SettingsHeader(copy: SettingsCopy, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, copy.back, tint = TextPrimary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(copy.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(copy.subtitle, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(NeonPurple, NeonFuchsia))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SettingsSection(icon: ImageVector, title: String, accent: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Surface(color = CardSurface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, TextMuted.copy(alpha = .14f)), tonalElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().padding(6.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
    badge: String? = null,
    error: String? = null,
    enabled: Boolean = true,
) {
    Column(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (badge != null) {
                        Surface(color = accent.copy(alpha = .2f), shape = RoundedCornerShape(6.dp)) {
                            Text(badge, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, enabled = enabled, onCheckedChange = null, colors = neonSwitchColors(accent))
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RepositoryRow(repository: CuratedRepository, enabled: Boolean, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(repository.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(repository.subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonPurple)
        } else {
            Switch(checked = enabled, onCheckedChange = onToggle, colors = neonSwitchColors(NeonPurple))
        }
    }
}

@Composable
private fun CustomRepositoryRow(url: String, removeLabel: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(url, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, removeLabel, tint = TextMuted) }
    }
}

@Composable
private fun ChoiceRow(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun UpdateRow(
    copy: SettingsCopy,
    state: SettingsUiState,
    versionName: String,
    onCheckUpdates: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onCheckUpdates).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(copy.checkUpdates, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            when (val status = state.updateCheck) {
                UpdateCheckStatus.Checking -> Text(copy.updateChecking, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                UpdateCheckStatus.UpToDate -> Text(copy.updateUpToDate.format(versionName), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                is UpdateCheckStatus.Available -> {
                    Text(copy.updateAvailable.format(status.version), style = MaterialTheme.typography.bodySmall, color = NeonFuchsia)
                    FilledTonalButton(
                        onClick = { onOpenLink(status.downloadUrl) },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonPurple.copy(alpha = .2f), contentColor = NeonPurple),
                    ) { Text(copy.updateDownload) }
                }
                UpdateCheckStatus.Idle -> {}
            }
        }
        if (state.updateCheck == UpdateCheckStatus.Checking) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonPurple)
        }
    }
}

@Composable
private fun AddRepositoryDialog(copy: SettingsCopy, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepOledBlack,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = { Text(copy.addRepositoryTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(copy.addRepositoryBody, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(copy.addRepositoryHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(url) }, enabled = url.isNotBlank()) { Text(copy.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(copy.cancel) } },
    )
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = TextMuted.copy(alpha = .12f))

@Composable
private fun neonSwitchColors(accent: Color) = SwitchDefaults.colors(
    checkedTrackColor = accent,
    checkedThumbColor = Color.White,
    checkedBorderColor = accent,
    uncheckedTrackColor = CardSurface,
    uncheckedThumbColor = TextMuted,
    uncheckedBorderColor = TextMuted.copy(alpha = .4f),
)
