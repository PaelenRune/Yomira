package com.yomira.domain.extension

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/** Pure display policy. Bad optional artwork must never invalidate a usable extension. */
object ExtensionLogo {
    fun resolveIcon(path: String?, repositoryUrl: String?): String? = runCatching {
        val value = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = URI(value)
        val resolved = if (uri.isAbsolute) uri else URI(secureUrl(repositoryUrl) ?: return null).resolve(uri)
        secureUrl(resolved.normalize().toString())
    }.getOrNull()

    fun favicon(repositoryUrl: String?): String? = secureUrl(repositoryUrl)?.let {
        URI(it).resolve("/favicon.ico").toString()
    }

    fun candidates(iconUrl: String?, repositoryUrl: String?): List<String> =
        listOfNotNull(resolveIcon(iconUrl, repositoryUrl), favicon(repositoryUrl)).distinct()

    fun initials(name: String): String = name.trim().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }.take(2)
        .joinToString("") { String(Character.toChars(it.codePointAt(0))) }
        .uppercase(Locale.ROOT).ifEmpty { "?" }

    /** URL-keyed favicons share one disk entry across every extension in a repository. */
    fun cacheKey(url: String): String {
        val canonical = requireNotNull(secureUrl(url)) { "Logo needs a secure URL" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "extension-logo-v1:" + digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun secureUrl(value: String?): String? = runCatching {
        val uri = URI(value?.trim()?.takeIf { it.isNotEmpty() } ?: return null)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null)
        val origin = URI("https", null, uri.host.lowercase(Locale.ROOT), if (uri.port == 443) -1 else uri.port,
            null, null, null).toASCIIString()
        // Keep escaped path/query bytes intact (CDN signatures may contain encoded delimiters).
        URI(origin + uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "")).normalize().toASCIIString()
    }.getOrNull()
}
