package com.yomira.data.metadata

import com.yomira.core.network.MetadataSources
import com.yomira.core.network.RemoteMetadata
import java.net.URI

/** Artwork quality is independent of the provider that owns the series identity.
 * Only already identity-matched records may be passed here. Adult records use
 * the same policy: missing landscape artwork remains null for the UI reflection.
 */
internal object ArtworkResolver {
    private val priority = listOf(
        MetadataSources.ANILIST, MetadataSources.KITSU, MetadataSources.MANGADEX,
        MetadataSources.COMICVINE, MetadataSources.BAKA_UPDATES, MetadataSources.OPENLIBRARY,
    )

    fun cover(records: List<RemoteMetadata>): String? = records
        .sortedWith(compareBy<RemoteMetadata> { coverLanguageRank(it) }.thenBy { sourceRank(it) })
        .firstNotNullOfOrNull { it.coverUrl?.trim()?.takeIf(::isSecureImage) }
    fun backdrop(records: List<RemoteMetadata>): String? = select(records) { it.bannerUrl }

    private fun select(records: List<RemoteMetadata>, field: (RemoteMetadata) -> String?): String? =
        records.sortedBy(::sourceRank)
            .firstNotNullOfOrNull { field(it)?.trim()?.takeIf(::isSecureImage) }

    private fun sourceRank(record: RemoteMetadata): Int =
        priority.indexOf(record.sourceId).takeIf { it >= 0 } ?: Int.MAX_VALUE

    private fun coverLanguageRank(record: RemoteMetadata): Int = when {
        record.coverLocale.equals("en", ignoreCase = true) -> 0
        // Explicit non-English evidence overrides provider and title hints.
        !record.coverLocale.isNullOrBlank() -> 3
        record.sourceId == MetadataSources.COMICVINE || record.sourceId == MetadataSources.OPENLIBRARY -> 0
        // An English title is only a hint when the provider omits the cover locale.
        !record.englishTitle.isNullOrBlank() || record.altTitles["en"].orEmpty().any { it.isNotBlank() } -> 1
        else -> 2
    }

    private fun isSecureImage(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: java.net.URISyntaxException) {
        false
    }
}
