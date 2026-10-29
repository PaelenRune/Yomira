package com.yomira.app.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yomira.app.R
import com.yomira.domain.extension.requireHttps
import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.feature.settings.CuratedRepositories

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExtensionsScreen(state: ExtensionsUiState, onBack: () -> Unit, onRefresh: () -> Unit,
    onQuery: (String) -> Unit, onFilter: (ExtensionFilter) -> Unit, onLanguage: (String) -> Unit,
    onAddRepository: (String) -> Unit, onRemoveRepository: (String) -> Unit, onAction: (String) -> Unit,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showRepositories by rememberSaveable { mutableStateOf(false) }
    var showOfficial by rememberSaveable { mutableStateOf(false) }
    val busy = state.loading || state.busyPackage != null
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.extensions_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        }, actions = {
            TextButton(onClick = onRefresh, enabled = !busy) { Text(stringResource(R.string.extensions_refresh)) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.extensions_repositories, state.repositories.size), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.extensions_trust), style = MaterialTheme.typography.bodySmall)
                        if (state.repositories.isEmpty() && !state.loading) {
                            Text(stringResource(R.string.extensions_no_repos))
                            FilledTonalButton(onClick = { showOfficial = true }, enabled = !busy) { Text(stringResource(R.string.repo_add_official)) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { showAdd = true }, enabled = !busy) { Text(stringResource(R.string.extensions_add_repo)) }
                            TextButton(onClick = { showRepositories = !showRepositories }) { Text(stringResource(R.string.extensions_manage_repos)) }
                        }
                        if (showRepositories) {
                            if (state.repositories.isEmpty()) Text(stringResource(R.string.extensions_no_repos))
                            state.repositories.forEach { url ->
                                val curated = CuratedRepositories.ALL.firstOrNull { it.indexUrl == url }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExtensionLogoImage(curated?.title ?: url, iconUrl = null, repositoryUrl = url)
                                    Column(Modifier.weight(1f)) {
                                        Text(curated?.title ?: url, style = MaterialTheme.typography.bodyMedium)
                                        if (curated == null) Text(url, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (curated != null) {
                                        Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
                                            Text(stringResource(R.string.extensions_repo_bundled),
                                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    TextButton(onClick = { onRemoveRepository(url) }, enabled = !busy) { Text(stringResource(R.string.extensions_remove_repo)) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(value = state.query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text(stringResource(R.string.extensions_search)) })
            }
            item {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExtensionFilter.entries.forEach { filter ->
                            FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, label = {
                                Text(stringResource(when (filter) {
                                    ExtensionFilter.ALL -> R.string.extensions_all
                                    ExtensionFilter.INSTALLED -> R.string.extensions_installed
                                    ExtensionFilter.UPDATES -> R.string.extensions_updates
                                }))
                            })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all", "tr", "en").forEach { language ->
                            FilterChip(selected = state.language == language, onClick = { onLanguage(language) }, label = {
                                Text(if (language == "all") stringResource(R.string.extensions_all_languages) else language.uppercase())
                            })
                        }
                    }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error) item {
                Text(stringResource(if (state.outdatedRepository) R.string.extensions_outdated_repository else R.string.extensions_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh, enabled = !busy) { Text(stringResource(R.string.extensions_refresh)) }
            }
            if (!state.loading && state.rows.isEmpty()) item {
                Text(stringResource(if (state.repositories.isEmpty()) R.string.extensions_no_repos else R.string.extensions_empty))
            }
            items(state.rows, key = { it.descriptor.packageName }) { row ->
                val descriptor = row.descriptor
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExtensionLogoImage(descriptor.name, descriptor.iconUrl, descriptor.repositoryUrl)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(descriptor.name, style = MaterialTheme.typography.titleMedium)
                            Text("v${descriptor.versionName} · ${descriptor.language.uppercase()}", style = MaterialTheme.typography.bodySmall)
                            if (descriptor.sources.isNotEmpty()) Text(descriptor.sources.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
                            if (descriptor.nsfw) Text("+18 / NSFW", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = { onAction(descriptor.packageName) }, enabled = !busy && row.action != ExtensionAction.BUILT_IN) {
                            if (state.busyPackage == descriptor.packageName) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(when (row.action) {
                                ExtensionAction.INSTALL -> R.string.extensions_install
                                ExtensionAction.UNINSTALL -> R.string.extensions_uninstall
                                ExtensionAction.UPDATE -> R.string.extensions_update
                                ExtensionAction.BUILT_IN -> R.string.extensions_builtin
                            }))
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                }
            }
        }
    }
    if (showOfficial) AlertDialog(onDismissRequest = { showOfficial = false },
        title = { Text(stringResource(R.string.repo_confirm_title)) },
        text = { Text(OnboardingWizardManager.KEIYOUSHI_INDEX) },
        confirmButton = { TextButton(onClick = { showOfficial = false; onAddRepository(OnboardingWizardManager.KEIYOUSHI_INDEX) }) { Text(stringResource(R.string.repo_add_official)) } },
        dismissButton = { TextButton(onClick = { showOfficial = false }) { Text(stringResource(R.string.extensions_cancel)) } })
    if (showAdd) {
        var url by rememberSaveable { mutableStateOf("") }
        val valid = url.isNotBlank() && runCatching { requireHttps(url.trim()) }.isSuccess
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(stringResource(R.string.extensions_add_repo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.extensions_repo_hint))
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("HTTPS URL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                }
            }, confirmButton = {
                TextButton(enabled = valid && !busy, onClick = { onAddRepository(url.trim()); showAdd = false }) { Text(stringResource(R.string.extensions_add_repo)) }
            }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.extensions_cancel)) } })
    }
}
