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

    fun cover(records: List<RemoteMetadata>): String? = select(records) { it.coverUrl }
    fun backdrop(records: List<RemoteMetadata>): String? = select(records) { it.bannerUrl }

    private fun select(records: List<RemoteMetadata>, field: (RemoteMetadata) -> String?): String? =
        records.sortedBy { priority.indexOf(it.sourceId).takeIf { rank -> rank >= 0 } ?: Int.MAX_VALUE }
            .firstNotNullOfOrNull { field(it)?.trim()?.takeIf(::isSecureImage) }

    private fun isSecureImage(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: java.net.URISyntaxException) {
        false
    }
}
