package com.yomira.core.network

/**
 * Shared transport configuration. The ComicVine key is intentionally injected
 * by the DI layer instead of being hard-coded in a build file.
 */
data class NetworkConfiguration(
    val userAgent: String = "Yomira/0.3.0-beta",
    val comicVineApiKey: String = "",
    val mangaUpdatesBaseUrl: String = "https://api.mangaupdates.com",
    val mangaUpdatesWebUrl: String = "https://www.mangaupdates.com",
    val aniListEndpoint: String = "https://graphql.anilist.co",
    val mangaDexBaseUrl: String = "https://api.mangadex.org",
    val mangaDexUploadsUrl: String = "https://uploads.mangadex.org",
    val kitsuBaseUrl: String = "https://kitsu.io/api/edge",
    val comicVineBaseUrl: String = "https://comicvine.gamespot.com/api",
    val openLibraryBaseUrl: String = "https://openlibrary.org",
    val openLibraryCoversUrl: String = "https://covers.openlibrary.org",
)
