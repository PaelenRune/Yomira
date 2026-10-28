package com.yomira.app.catalog

import com.yomira.domain.extension.*

/** App-owned API adapter; never claims that an upstream APK has been installed or trusted. */
class NativeSourceRepository(
    private val delegate: ExtensionRepository,
    private val includeNative: () -> Boolean = { true },
) : ExtensionRepository by delegate {
    override suspend fun available(): List<ExtensionDescriptor> = delegate.available() + if (includeNative()) listOf(mangaDex) else emptyList()
    override suspend fun refresh(): List<ExtensionDescriptor> = delegate.refresh() + if (includeNative()) listOf(mangaDex) else emptyList()
    override suspend fun installed(): List<InstalledExtension> =
        delegate.installed() + if (includeNative()) listOf(InstalledExtension(PACKAGE, 1, trusted = true)) else emptyList()

    companion object {
        const val PACKAGE = "com.yomira.native.mangadex"
        val mangaDex = ExtensionDescriptor(
            packageName = PACKAGE,
            name = "MangaDex (built-in)",
            language = "all",
            nsfw = false,
            versionName = "1",
            versionCode = 1,
            apkUrl = "https://mangadex.org/",
            sources = listOf(ExtensionSource("mangadex-en", "MangaDex", "en"), ExtensionSource("mangadex-tr", "MangaDex", "tr")),
        )
    }
}
