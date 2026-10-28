// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) CatalogueSource
// ABI. The audited DEXes override lang, supportsRelatedMangas,
// disableRelatedMangas(BySearch) and fetchRelatedMangaList on this interface.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SManga

@Suppress("Unused")
interface CatalogueSource : Source {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    val lang: String

    /**
     * Whether parsing related mangas in manga page or extension provide custom related mangas request.
     * Only Supported on Komikku
     * @default false
     */
    val supportsRelatedMangas: Boolean get() = false

    /**
     * Extensions doesn't want to use App's [getRelatedMangaListBySearch].
     * Only Supported on Komikku
     * @default false
     */
    val disableRelatedMangasBySearch: Boolean get() = false

    /**
     * Disable showing any related titles.
     * Only Supported on Komikku
     * @default false
     */
    val disableRelatedMangas: Boolean get() = false

    /**
     * Fetch related mangas for a manga from source/site.
     * Only Supported on Komikku
     *
     * @param manga the current manga to get related mangas.
     * @return the related mangas for the current manga.
     * @throws UnsupportedOperationException if a source doesn't support related mangas.
     */
    suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> =
        throw UnsupportedOperationException("Unsupported!")
}
