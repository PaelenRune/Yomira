package com.yomira.domain.extension

import java.net.URI
import java.net.URLDecoder

sealed interface RepoDeepLinkResult {
    data class Valid(val indexUrl: String) : RepoDeepLinkResult
    data object Invalid : RepoDeepLinkResult
    /** Not a repository deep link at all; callers ignore it. */
    data object NotRepoLink : RepoDeepLinkResult
}

/**
 * Parses `yomira://add-repo`, `yomira://repo` and `tachiyomi://add-repo` links carrying the
 * index in `url=` (or `index=`). Only absolute https URLs without credentials are accepted.
 */
object RepoDeepLink {
    private val accepted = setOf("yomira" to "add-repo", "yomira" to "repo", "tachiyomi" to "add-repo")

    fun parse(link: String?): RepoDeepLinkResult {
        if (link.isNullOrBlank()) return RepoDeepLinkResult.NotRepoLink
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return if (
            accepted.any { (scheme, host) -> link.startsWith("$scheme://$host?", ignoreCase = true) }
        ) RepoDeepLinkResult.Invalid else RepoDeepLinkResult.NotRepoLink
        val scheme = uri.scheme?.lowercase() ?: return RepoDeepLinkResult.NotRepoLink
        val host = uri.host?.lowercase() ?: uri.rawAuthority?.lowercase() ?: return RepoDeepLinkResult.NotRepoLink
        if ((scheme to host) !in accepted) return RepoDeepLinkResult.NotRepoLink
        if (uri.rawUserInfo != null || uri.port != -1) return RepoDeepLinkResult.Invalid
        val params = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
            key to value
        }
        val candidate = (params["url"] ?: params["index"])?.trim()
        return if (candidate != null && isValidIndexUrl(candidate)) RepoDeepLinkResult.Valid(candidate)
        else RepoDeepLinkResult.Invalid
    }

    fun isValidIndexUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            runCatching { requireHttps(url) }.isSuccess
    }.getOrDefault(false)
}
