package com.yomira.domain.extension

interface ExtensionRepository {
    suspend fun addRepository(indexUrl: String)
    /** Withdraws consent for a repository; implementations must also drop its cached entries. */
    suspend fun removeRepository(indexUrl: String)
    suspend fun repositories(): List<String>
    suspend fun refresh(): List<ExtensionDescriptor>
    suspend fun available(): List<ExtensionDescriptor>
    suspend fun installed(): List<InstalledExtension>
    /** Index that supplied the current package; never infer ownership from its APK URL. */
    suspend fun repositoryUrl(packageName: String): String? = null
}

interface SourceBridge {
    suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int = 1): List<SourceManga>
    suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga): List<SourceChapter>
    suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter): List<SourcePage>
}

/**
 * Optional external catalog for UNINSTALLED sources. Repo indexes contain no titles or chapter counts.
 * Implementations must match series identity, return factual observations, and not execute untrusted APKs.
 */
fun interface SourceDiscovery {
    suspend fun discover(series: SeriesIdentity, extensions: List<ExtensionDescriptor>): List<SourceCandidate>
}

interface OnboardingStore {
    fun observe(): kotlinx.coroutines.flow.Flow<OnboardingState?> = kotlinx.coroutines.flow.flow { emit(read()) }
    suspend fun read(): OnboardingState?
    /** Atomically persist the complete state; implementations should use DataStore or a transaction. */
    suspend fun write(state: OnboardingState)
}

data class OnboardingState(
    val preferences: ReaderPreferences,
    val repositoryUrl: String?,
    val suggestedPackages: List<String>,
    val completed: Boolean,
)
