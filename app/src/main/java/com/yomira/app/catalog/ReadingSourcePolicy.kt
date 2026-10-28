package com.yomira.app.catalog

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.isVisibleForReader
import com.yomira.domain.extension.ReaderPreferences

/** Source warnings describe the whole catalog, not the rating of the requested title. */
internal fun readingSourcePreferences(manga: Manga, stored: ReaderPreferences, unlocked: Boolean): ReaderPreferences? {
    val preferences = stored.copy(allowAdultContent = stored.isAdult && stored.allowAdultContent)
    if (!manga.isVisibleForReader(preferences.excludedTags, preferences.isAdult, preferences.allowAdultContent) ||
        (manga.contentRating == ContentRating.EXPLICIT && !unlocked)) return null
    return preferences
}
