// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa); required by the
// audited MangaFire/WeebCentral/Toonily DEXes (HttpException.<init>(I)V).
package eu.kanade.tachiyomi.network

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 * Use it to have a standardized error message in the app across extensions.
 *
 * @see okhttp3.Response.isSuccessful
 * @param code [Int] the HTTP status code
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
