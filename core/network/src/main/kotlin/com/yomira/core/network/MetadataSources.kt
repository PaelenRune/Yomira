package com.yomira.core.network

/**
 * Stable identifiers for every metadata provider. These values are persisted
 * as part of a manga id (`sourceId:remoteId`), therefore they must never change.
 */
object MetadataSources {
    const val BAKA_UPDATES = "bakaupdates"
    const val ANILIST = "anilist"
    const val MANGADEX = "mangadex"
    const val KITSU = "kitsu"
    const val COMICVINE = "comicvine"
    const val OPENLIBRARY = "openlibrary"

    val all: Set<String> = setOf(BAKA_UPDATES, ANILIST, MANGADEX, KITSU, COMICVINE, OPENLIBRARY)
}
