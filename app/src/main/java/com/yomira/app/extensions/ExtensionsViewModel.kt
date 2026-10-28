package com.yomira.app.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yomira.app.catalog.NativeSourceRepository
import com.yomira.data.extension.SandboxExtensionInstaller
import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.InstalledExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExtensionFilter { ALL, INSTALLED, UPDATES }
enum class ExtensionAction { INSTALL, UNINSTALL, UPDATE, BUILT_IN }

data class ExtensionRow(val descriptor: ExtensionDescriptor, val installedVersion: Long? = null) {
    val action: ExtensionAction get() = when {
        descriptor.packageName == NativeSourceRepository.PACKAGE -> ExtensionAction.BUILT_IN
        installedVersion == null -> ExtensionAction.INSTALL
        descriptor.versionCode > installedVersion -> ExtensionAction.UPDATE
        else -> ExtensionAction.UNINSTALL
    }
}

data class ExtensionsUiState(
    val repositories: List<String> = emptyList(),
    val available: List<ExtensionDescriptor> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val loading: Boolean = false,
    val busyPackage: String? = null,
    val query: String = "",
    val filter: ExtensionFilter = ExtensionFilter.ALL,
    val language: String = "all",
    val error: Boolean = false,
    val rows: List<ExtensionRow> = emptyList(),
)

private data class ExtensionRowInputs(
    val available: List<ExtensionDescriptor>,
    val installed: List<InstalledExtension>,
    val query: String,
    val filter: ExtensionFilter,
    val language: String,
) {
    constructor(state: ExtensionsUiState) : this(state.available, state.installed, state.query, state.filter, state.language)

    fun buildRows(): List<ExtensionRow> {
        val installedByPackage = installed.associateBy { it.packageName }
        return (available + installed.mapNotNull { it.descriptor }).distinctBy { it.packageName }
            .map { ExtensionRow(it, installedByPackage[it.packageName]?.versionCode) }
            .filter { row ->
                val entry = row.descriptor
                (query.isBlank() || entry.name.contains(query.trim(), true) ||
                    entry.sources.any { it.name.contains(query.trim(), true) }) &&
                    (language == "all" || entry.language.equals(language, true) ||
                        entry.language in setOf("all", "multi", "mul") || entry.sources.any { it.language.equals(language, true) }) &&
                    when (filter) {
                        ExtensionFilter.ALL -> true
                        ExtensionFilter.INSTALLED -> row.installedVersion != null
                        ExtensionFilter.UPDATES -> row.action == ExtensionAction.UPDATE
                    }
            }.sortedBy { it.descriptor.name.lowercase(java.util.Locale.ROOT) }
    }
}

/** Small boundary lets tests exercise UI mutations without Android or real APK downloads. */
interface ExtensionOperations {
    suspend fun install(descriptor: ExtensionDescriptor, repositoryUrl: String)
    suspend fun uninstall(packageName: String)
}

class SandboxExtensionOperations @Inject constructor(private val installer: SandboxExtensionInstaller) : ExtensionOperations {
    override suspend fun install(descriptor: ExtensionDescriptor, repositoryUrl: String) {
        installer.install(descriptor, repositoryUrl)
    }
    override suspend fun uninstall(packageName: String) { installer.uninstall(packageName) }
}

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repository: ExtensionRepository,
    private val operations: ExtensionOperations,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExtensionsUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            mutableState.map { ExtensionRowInputs(it) }.distinctUntilChanged().collect { inputs ->
                val rows = inputs.buildRows()
                mutableState.update { current ->
                    // Never publish rows for inputs replaced while calculation was running.
                    if (ExtensionRowInputs(current) == inputs) current.copy(rows = rows) else current
                }
            }
        }
    }

    fun setQuery(value: String) { mutableState.update { it.copy(query = value) } }
    fun setFilter(value: ExtensionFilter) { mutableState.update { it.copy(filter = value) } }
    fun setLanguage(value: String) { mutableState.update { it.copy(language = value) } }

    fun load() = change { loadCached() }
    fun refresh() = change { loadCached(); mutableState.update { it.copy(available = repository.refresh()) } }
    fun addRepository(url: String) = change {
        repository.addRepository(url.trim())
        loadCached()
        mutableState.update { it.copy(available = repository.refresh()) }
    }
    fun removeRepository(url: String) = change {
        repository.removeRepository(url)
        loadCached()
        mutableState.update { it.copy(available = repository.refresh()) }
    }

    fun act(packageName: String) {
        val row = mutableState.value.rows.firstOrNull { it.descriptor.packageName == packageName } ?: return
        if (row.action == ExtensionAction.BUILT_IN) return
        change(packageName) {
            if (row.action == ExtensionAction.UNINSTALL) operations.uninstall(packageName)
            else {
                val origin = requireNotNull(repository.repositoryUrl(packageName)) { "Repository no longer available" }
                require(origin in repository.repositories()) { "Repository consent revoked" }
                operations.install(row.descriptor, origin)
            }
            loadCached()
        }
    }

    private suspend fun loadCached() {
        val repos = repository.repositories()
        val installed = repository.installed()
        val available = repository.available()
        mutableState.update { it.copy(repositories = repos, installed = installed, available = available) }
    }

    private fun change(packageName: String? = null, block: suspend () -> Unit) {
        if (mutableState.value.loading || mutableState.value.busyPackage != null) return
        mutableState.update { it.copy(loading = packageName == null, busyPackage = packageName, error = false) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(error = true) }
            } finally {
                mutableState.update { it.copy(loading = false, busyPackage = null) }
            }
        }
    }
}
