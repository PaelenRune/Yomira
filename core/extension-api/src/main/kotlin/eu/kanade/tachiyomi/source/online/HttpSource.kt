// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) HttpSource ABI
// with the real Mihon runtime behaviors (id generation, default headers,
// image request, setUrlWithoutDomain) so extension DEXes both link and run
// as they would inside a Mihon-compatible host.
package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/**
 * A simple implementation for sources from a website.
 */
@Suppress("UNUSED", "UnusedReceiverParameter")
abstract class HttpSource : CatalogueSource {

    /**
     * Network service. Delegates to the host-injected client via
     * [NetworkHelper]; see eu.kanade.tachiyomi.shim.ShimServiceLocator.
     */
    protected val network: NetworkHelper = NetworkHelper()

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    /**
     * Returns the base (home) URL of the website as a string.
     *
     * This is typically the root address that serves as the main entry point
     * to the site's content, such as "https://mihon.tech".
     *
     * @return The website's home page URL. Defaults to [baseUrl].
     */
    open fun getHomeUrl(): String = baseUrl

    /**
     * Version id used to generate the source id. If the site completely changes and urls are
     * incompatible, you may increase this value and it'll be considered as a new source.
     */
    open val versionId: Int = 1

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: `"${name.lowercase()}/$lang/$versionId"`.
     * Note the generated id sets the sign bit to `0`.
     */
    override val id: Long by lazy { generateId(name, lang, versionId) }

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient get() = network.client

    /**
     * Generates a unique ID for the source based on the provided [name], [lang] and
     * [versionId]. It will use the first 16 characters (64 bits) of the MD5 of the string
     * `"${name.lowercase()}/$lang/$versionId"`.
     *
     * Note: the generated ID sets the sign bit to `0`.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgent)
    }

    /**
     * Visible name of the source.
     */
    override fun toString(): String = "$name (${lang.uppercase()})"

    /**
     * Returns the image url for the provided [page]. The function is only called if
     * [Page.imageUrl] is null.
     *
     * @param page the page whose source image has to be fetched.
     */
    open suspend fun getImageUrl(page: Page): String =
        page.imageUrl ?: error("Page.imageUrl is null for page ${page.index}")

    /**
     * Returns the request for getting the source image. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param page the chapter whose page list has to be fetched
     */
    protected open fun imageRequest(page: Page): Request =
        GET(page.imageUrl ?: error("Page.imageUrl is null for page ${page.index}"), headers)

    /**
     * Assigns the url of the chapter without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the chapter.
     */
    @Suppress("Unused")
    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Assigns the url of the manga without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the manga.
     */
    @Suppress("Unused")
    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Returns the url of the given string without the scheme and domain.
     *
     * @param orig the full url.
     */
    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig.replace(" ", "%20"))
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (out.isNullOrEmpty()) {
                throw IllegalArgumentException("Invalid URL: $orig")
            }
            out
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException(e)
        }
    }

    /**
     * Returns the url of the provided manga
     *
     * @param manga the manga
     * @return url of the manga
     */
    abstract fun getMangaUrl(manga: SManga): String

    /**
     * Returns the url of the provided chapter
     *
     * @param chapter the chapter
     * @return url of the chapter
     */
    abstract fun getChapterUrl(chapter: SChapter): String
}
