package com.yomira.app.runtime

import android.content.Context
import com.yomira.data.extension.ExtensionConsentInteraction
import com.yomira.data.extension.ExtensionTrust
import com.yomira.data.extension.IsolatedExtensionRuntime
import com.yomira.data.extension.JsonSourceManifest
import com.yomira.data.extension.SandboxIntegrityGuard
import com.yomira.domain.extension.SourceChapter
import com.yomira.domain.extension.SourceManga
import com.yomira.domain.extension.SourcePage
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.shim.ShimServiceLocator
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * Production in-process extension runtime (charter DEC-V811-RUNTIME-A).
 *
 * Loads consented, installed extensions' extracted dex payloads with a
 * [DexClassLoader] whose parent is the app classloader, so third-party code
 * links against the `:core:extension-api` compatibility shims and the
 * HOST-PROVIDED libraries (runtime-link-inventory.md). This is deliberately
 * *not* an OS-level sandbox: extensions run with the app's permissions, and the
 * trust boundary is manifest inspection + dex-integrity pinning + first-use
 * consent (see data/extension/README.md).
 *
 * Fail-closed rules, enforced on every invocation:
 * 1. [SandboxIntegrityGuard.requireLoadable] re-hashes the extracted dex files
 *    against the manifest the user consented to before any code is used.
 * 2. [ExtensionTrust.allow] re-checks first-use consent (fingerprint-bound;
 *    v2-only fingerprints re-prompt after reinstall). Without an
 *    [ExtensionConsentInteraction] in the calling context, background callers
 *    never prompt and simply fail closed.
 * 3. The requested [sourceId] must be one of the manifest's declared sources.
 *
 * The classloader itself is cached per package (keyed by version + dex hashes,
 * so an update or re-install produces a fresh loader), but the checks above run
 * on every call regardless of the cache.
 */
class AndroidSourceRuntime(
    private val guard: SandboxIntegrityGuard,
    private val trust: ExtensionTrust,
    client: OkHttpClient,
    private val environment: ExtensionRuntimeEnvironment,
) : IsolatedExtensionRuntime {

    init {
        // Must happen before any extension class initializes: the network shims
        // delegate to the host's instrumented client (StealthShield DoH, TLS and
        // timeouts included) and fail fast with ShimNotSupportedException until
        // this is configured.
        ShimServiceLocator.configure(client)
    }

    private val loadMutex = Mutex()

    /** One live loader per package; replaced when version/dex hashes change. */
    private val loadedByPackage = HashMap<String, LoadedExtension>()

    override suspend fun verifyAccess(packageName: String, sourceId: String) {
        source(packageName, sourceId)
    }

    override suspend fun getMangaList(
        packageName: String,
        sourceId: String,
        query: String,
        page: Int,
    ): List<SourceManga> {
        require(query.isNotBlank() && page > 0)
        val (manifest, source) = source(packageName, sourceId)
        val result = source.getSearchManga(page, query, source.getFilterList())
        return result.mangas.map { manga ->
            SourceManga(
                id = manga.url,
                title = manga.title,
                url = webUrl(source, manga),
            )
        }.also { rows ->
            // The routing id is the dex's own manga url; a blank one could never
            // be resolved back into a chapter list.
            if (rows.isNotEmpty()) require(rows.all { it.id.isNotBlank() }) {
                "Source ${manifest.packageName}/$sourceId returned a manga without a url"
            }
        }
    }

    override suspend fun getChapterList(
        packageName: String,
        sourceId: String,
        manga: SourceManga,
    ): List<SourceChapter> {
        val (_, source) = source(packageName, sourceId)
        val request = SManga.create().apply {
            url = manga.id
            title = manga.title
            initialized = true
        }
        val update = source.getMangaUpdate(request, emptyList(), fetchDetails = false, fetchChapters = true)
        return update.chapters.map { chapter ->
            SourceChapter(
                id = chapter.url,
                name = chapter.name,
                number = chapter.chapter_number.takeUnless { it == -1f }?.toDouble(),
                uploadedAt = chapter.date_upload.takeUnless { it <= 0L },
            )
        }.distinctBy { it.id }
    }

    override suspend fun getPageList(
        packageName: String,
        sourceId: String,
        chapter: SourceChapter,
    ): List<SourcePage> {
        val (_, source) = source(packageName, sourceId)
        val request = SChapter.create().apply {
            url = chapter.id
            name = chapter.name
            chapter_number = chapter.number?.toFloat() ?: -1f
            date_upload = chapter.uploadedAt ?: 0L
        }
        // Image resolution mirrors the Mihon reader contract: a page whose
        // imageUrl is still null is resolved through the source (the "image
        // source" step), then served with the source's request headers
        // (Referer/User-Agent) so CDN-protected images load.
        val httpSource = source as? HttpSource
        return source.getPageList(request).mapIndexed { index, page ->
            val url = page.imageUrl ?: httpSource?.getImageUrl(page)
                ?: throw ExtensionSourceException(
                    "Page ${page.index} of chapter ${chapter.id} has no image url and the source cannot resolve it",
                )
            SourcePage(index = page.index.takeIf { it >= 0 } ?: index, imageUrl = url, headers = imageHeaders(httpSource))
        }
    }

    private fun imageHeaders(source: HttpSource?): Map<String, String> {
        if (source == null) return emptyMap()
        return runCatching {
            // names() keeps the declared casing (toMultimap() in OkHttp 5
            // lowercases keys); lookups are case-insensitive either way.
            val headers = source.headers
            buildMap { for (name in headers.names()) put(name, headers[name] ?: "") }
        }.getOrDefault(emptyMap())
    }

    /** Resolves a full web url for display; the dex's url is site-relative. */
    private fun webUrl(source: Source, manga: SManga): String =
        runCatching { (source as? HttpSource)?.getMangaUrl(manga) }.getOrNull()?.takeIf { it.isNotBlank() } ?: manga.url

    /**
     * The single entry point every operation funnels through: integrity +
     * consent + source ownership, then the cached loader.
     */
    private suspend fun source(packageName: String, sourceId: String): Pair<JsonSourceManifest, Source> {
        require(packageName.isNotBlank() && sourceId.isNotBlank())
        val loadable = try {
            guard.requireLoadable(packageName)
        } catch (error: SecurityException) {
            throw ExtensionIntegrityException("Sandbox for $packageName failed the integrity check", error)
        } catch (error: CancellationException) {
            throw error
        }
        val manifest = loadable.manifest
        // Fail closed: no consent record (or a fingerprint change) stops here.
        // Foreground reading may prompt through the RT-T coordinator via the
        // ExtensionConsentInteraction context element; background callers cannot.
        if (!trust.allow(manifest, currentCoroutineContext()[ExtensionConsentInteraction])) {
            throw ExtensionConsentDeniedException(
                "First-use consent for ${manifest.packageName} was not granted; refusing to load its dex",
            )
        }
        if (manifest.sources.none { it.id == sourceId }) {
            throw ExtensionSourceException("Source $sourceId is not declared by ${manifest.packageName}")
        }
        val loaded = loaded(packageName, loadable)
        val source = loaded.sourcesById[sourceId]
            ?: throw ExtensionSourceException(
                "Installed dex of ${manifest.packageName} exposes sources " +
                    "${loaded.sourcesById.keys.joinToString(prefix = "[", postfix = "]")}; none matches $sourceId",
            )
        return manifest to source
    }

    private suspend fun loaded(packageName: String, loadable: SandboxIntegrityGuard.LoadableSandbox): LoadedExtension {
        val key = cacheKey(loadable.manifest)
        loadedByPackage[packageName]?.takeIf { it.key == key }?.let { return it }
        return loadMutex.withLock {
            loadedByPackage[packageName]?.takeIf { it.key == key }?.let { return it }
            // Host services (Injekt globals, dex output dir) must be ready before
            // any extension class initializes.
            environment.prepareHostServices()
            val created = create(packageName, key, loadable)
            loadedByPackage[packageName] = created
            created
        }
    }

    private fun create(
        packageName: String,
        key: String,
        loadable: SandboxIntegrityGuard.LoadableSandbox,
    ): LoadedExtension {
        val classLoader = environment.createClassLoader(loadable.dexPaths, environment.optimizedDexDirectory())
        val entryClasses = loadable.manifest.entryClasses + GENERATED_ENTRY_CLASS
        val sources = mutableListOf<Source>()
        val problems = mutableListOf<String>()
        for (entryClass in entryClasses.distinct()) {
            try {
                when (val instance = classLoader.loadClass(entryClass).getDeclaredConstructor().newInstance()) {
                    is SourceFactory -> sources += instance.createSources()
                    is Source -> sources += instance
                    else -> problems += "$entryClass is neither SourceFactory nor Source"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                problems += "$entryClass: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        if (sources.isEmpty()) {
            throw ExtensionLinkException(
                "No sources could be created from ${packageName}'s dex (entry classes: " +
                    "${entryClasses.joinToString()}; problems: ${problems.joinToString()})",
            )
        }
        val sourcesById = sources.associateBy { it.id.toString() }
        return LoadedExtension(key, sources, sourcesById)
    }

    private fun cacheKey(manifest: JsonSourceManifest): String = buildString {
        append(manifest.packageName).append('\n')
        append(manifest.versionCode).append('\n')
        manifest.dexFiles.sortedBy { it.name }.forEach { append(it.name).append(':').append(it.sha256).append('\n') }
    }

    private data class LoadedExtension(
        val key: String,
        val sources: List<Source>,
        val sourcesById: Map<String, Source>,
    )

    companion object {
        /**
         * Keiyoushi extension APKs are minified, so the historical
         * `<Package>Extension` names do not survive R8. extensions-lib v1.6
         * instead ships a generated entry point implementing SourceFactory
         * under this fixed name (verified in the MangaFire, Weeb Central and
         * Toonily fixture dexes).
         */
        const val GENERATED_ENTRY_CLASS = "keiyoushi.source.Generated"
    }
}

/** Everything the runtime needs from the Android environment; seams for tests. */
interface ExtensionRuntimeEnvironment {
    /** Creates the loader for verified dex payloads; parent must be the app classloader. */
    fun createClassLoader(dexPaths: List<Path>, optimizedDirectory: File): ClassLoader

    /** App-private directory for optimized dex output. */
    fun optimizedDexDirectory(): File

    /** Idempotently initializes the global Injekt scope extension code may read. */
    fun prepareHostServices()
}

/** Production environment: DexClassLoader over the app's private storage. */
class DexClassLoaderEnvironment(
    private val context: Context,
) : ExtensionRuntimeEnvironment {

    override fun createClassLoader(dexPaths: List<Path>, optimizedDirectory: File): ClassLoader = DexClassLoader(
        dexPaths.joinToString(File.pathSeparator) { it.toString() },
        optimizedDirectory.absolutePath,
        context.applicationInfo?.nativeLibraryDir,
        context.classLoader,
    )

    override fun optimizedDexDirectory(): File = context.codeCacheDir

    override fun prepareHostServices() {
        InjektHost.initialize(context)
    }
}

/** Typed failures surfaced through the SandboxSourceBridge contract. */
sealed class AndroidSourceRuntimeException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Fail-closed consent: the extension's dex is never touched. */
class ExtensionConsentDeniedException(message: String) : AndroidSourceRuntimeException(message)

/** The extracted dex no longer matches the consented manifest. */
class ExtensionIntegrityException(message: String, cause: Throwable) : AndroidSourceRuntimeException(message, cause)

/** The requested source id is unknown to the package. */
class ExtensionSourceException(message: String) : AndroidSourceRuntimeException(message)

/** The dex could not be linked: entry classes missing or not constructible. */
class ExtensionLinkException(message: String) : AndroidSourceRuntimeException(message)
