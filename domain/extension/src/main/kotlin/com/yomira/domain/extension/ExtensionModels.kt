package com.yomira.domain.extension

import java.net.URI

enum class ReadingLanguage(val code: String) {
    TURKISH("tr"), ENGLISH("en"), BOTH("both");

    /** Both means Turkish and English, not every language in the upstream catalogs. */
    fun accepts(sourceLanguage: String): Boolean {
        val base = sourceLanguage.trim().lowercase(java.util.Locale.ROOT).substringBefore('-').substringBefore('_')
        return base in setOf("all", "multi", "mul") ||
            if (this == BOTH) base == "tr" || base == "en" else base == code
    }
}

data class ReaderPreferences(
    val language: ReadingLanguage,
    val allowAdultContent: Boolean = false,
    val isAdult: Boolean = false,
    val excludedTags: Set<String> = emptySet(),
    val favoriteGenres: Set<String> = emptySet(),
)

data class ExtensionSource(val id: String, val name: String, val language: String)

data class ExtensionDescriptor(
    val packageName: String,
    val name: String,
    val language: String,
    val nsfw: Boolean,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val sources: List<ExtensionSource>,
) {
    init {
        require(packageName.isNotBlank() && versionCode > 0)
        requireHttps(apkUrl)
    }
}

data class InstalledExtension(val packageName: String, val versionCode: Long, val trusted: Boolean)
data class ExtensionUpdate(val installed: InstalledExtension, val available: ExtensionDescriptor)
data class SourceManga(val id: String, val title: String, val url: String)
data class SourceChapter(val id: String, val name: String, val number: Double?, val uploadedAt: Long?)
data class SourcePage(val index: Int, val imageUrl: String, val headers: Map<String, String> = emptyMap())

/** A stable external ID and aliases prevent unrelated similarly named series being merged. */
data class SeriesIdentity(val id: String, val title: String, val aliases: Set<String> = emptySet())

enum class SourceHealth { UNKNOWN, HEALTHY, BLOCKED }

data class SourceCandidate(
    val extension: ExtensionDescriptor,
    val source: ExtensionSource,
    val manga: SourceManga,
    val chapterCount: Int,
    val latestChapterAt: Long?,
    val health: SourceHealth = SourceHealth.UNKNOWN,
    val responseTimeMillis: Long? = null,
) {
    init {
        require(chapterCount >= 0)
        require(source in extension.sources)
        require(responseTimeMillis == null || responseTimeMillis >= 0)
    }
}

sealed interface SmartMatchResult {
    data class MissingExtension(
        val suggestedSource: SourceCandidate,
        val chapterCount: Int,
        val installUrl: String,
        val alternatives: List<SourceCandidate> = emptyList(),
    ) : SmartMatchResult

    data class Ready(
        val selected: SourceCandidate,
        val chapters: List<SourceChapter>,
        val alternatives: List<SourceCandidate>,
    ) : SmartMatchResult {
        val canRead: Boolean get() = chapters.isNotEmpty()
    }

    data object NoMatch : SmartMatchResult
    data class Unavailable(val failures: List<SourceFailure>) : SmartMatchResult
}

data class SourceFailure(val sourceId: String, val reason: String)

fun requireHttps(value: String) {
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
        "An absolute HTTPS URL without credentials is required"
    }
}
