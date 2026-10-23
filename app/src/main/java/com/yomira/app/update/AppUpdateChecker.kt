package com.yomira.app.update

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** What the update dialog needs; never carries credentials or user data. */
data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String? = null,
)

/**
 * Compares [BuildConfig.VERSION_NAME][com.yomira.app.BuildConfig.VERSION_NAME]
 * with the latest GitHub release tag.
 *
 * All failures collapse to `null`: an update check must never block the app,
 * surface a scary error, or nag the user when the network is unavailable.
 */
class AppUpdateChecker(
    private val httpClient: OkHttpClient,
    private val currentVersion: String,
    private val endpoint: HttpUrl = DEFAULT_RELEASES_URL.toHttpUrl(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val userAgent: String = "Yomira-UpdateChecker",
) {
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun fetch(): UpdateInfo? {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            if (body.length > MAX_PAYLOAD_CHARS) return@use null
            val release = json.decodeFromString<GitHubRelease>(body)
            if (release.draft) return@use null

            val tag = release.tagName?.trim().orEmpty()
            if (tag.isEmpty() || !AppVersion.isNewer(tag, currentVersion)) return@use null

            val pageUrl = release.htmlUrl.httpsOrNull()
            val apkUrl = release.assets
                .firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
                ?.browserDownloadUrl
                .httpsOrNull()
            // Without an APK asset the release page still lets the user install manually.
            val downloadUrl = apkUrl ?: pageUrl ?: return@use null

            UpdateInfo(
                version = tag,
                releaseNotes = release.body.cleanNotes(),
                apkDownloadUrl = downloadUrl,
                releasePageUrl = pageUrl,
            )
        }
    }

    private fun String?.httpsOrNull(): String? {
        val value = this?.trim().orEmpty()
        val url = value.toHttpUrlOrNull() ?: return null
        return value.takeIf { url.isHttps && url.host.isNotBlank() }
    }

    private fun String?.cleanNotes(): String {
        val value = this?.trim().orEmpty()
        return value.ifEmpty { DEFAULT_NOTES }.take(MAX_NOTES_CHARS)
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )

    companion object {
        const val DEFAULT_RELEASES_URL = "https://api.github.com/repos/PaelenRune/Yomira/releases/latest"
        private const val MAX_PAYLOAD_CHARS = 512 * 1024
        private const val MAX_NOTES_CHARS = 4_000
        private const val DEFAULT_NOTES = "Performans ve kararlılık iyileştirmeleri."
    }
}

/**
 * Minimal SemVer comparison for release tags.
 *
 * Handles `v` prefixes, build metadata, multi-segment cores and pre-release
 * suffixes such as `-beta`, `-beta.2` and `-rc.1`.
 */
object AppVersion {
    fun isNewer(candidate: String, current: String): Boolean {
        val incoming = parse(candidate) ?: return false
        val installed = parse(current) ?: return false

        for (index in 0 until maxOf(incoming.core.size, installed.core.size)) {
            val left = incoming.core.getOrElse(index) { 0 }
            val right = installed.core.getOrElse(index) { 0 }
            if (left != right) return left > right
        }

        // Same core version: a final release outranks any pre-release.
        if (incoming.pre.isEmpty() && installed.pre.isEmpty()) return false
        if (incoming.pre.isEmpty()) return true
        if (installed.pre.isEmpty()) return false

        for (index in 0 until maxOf(incoming.pre.size, installed.pre.size)) {
            val left = incoming.pre.getOrNull(index) ?: return false
            val right = installed.pre.getOrNull(index) ?: return true
            if (left.equals(right, ignoreCase = true)) continue
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            return when {
                leftNumber != null && rightNumber != null -> leftNumber > rightNumber
                leftNumber != null -> false   // numeric identifiers sort before alphanumeric ones
                rightNumber != null -> true
                else -> left.lowercase(Locale.ROOT) > right.lowercase(Locale.ROOT)
            }
        }
        return false
    }

    private data class Version(val core: List<Int>, val pre: List<String>)

    private fun parse(raw: String): Version? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) trimmed.substring(1) else trimmed
        val withoutBuild = withoutPrefix.substringBefore('+')
        val corePart = withoutBuild.substringBefore('-')
        val prePart = withoutBuild.substringAfter('-', missingDelimiterValue = "")
        if (corePart.isEmpty()) return null
        val core = corePart.split('.').map { segment -> segment.toIntOrNull() ?: return null }
        val pre = prePart.split('.').filter { it.isNotBlank() }
        return Version(core, pre)
    }
}
