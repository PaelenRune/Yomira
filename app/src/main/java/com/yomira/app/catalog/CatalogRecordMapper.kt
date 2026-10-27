package com.yomira.app.catalog

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.WesternComics
import com.yomira.core.network.RemoteMetadata
import com.yomira.domain.classifier.ClassificationInput
import com.yomira.domain.classifier.ContentRatingClassifier
import java.util.Locale

/**
 * Converts a provider-neutral record into the persisted catalogue model.
 *
 * This is the single conversion point for both first-launch trending warming and
 * the infinite feed, so the English-display-title rule, the explicit-content
 * gate and the western-comics genre marker can never drift between the two.
 */
object CatalogRecordMapper {
    const val MAX_TITLE_LENGTH = 200

    fun toMangaOrNull(
        record: RemoteMetadata,
        classifier: ContentRatingClassifier,
        allowExplicit: Boolean = false,
    ): Manga? {
        // English display title first: the shelf shows *Attack on Titan*, not the
        // AniList romaji *Shingeki no Kyojin*.
        val canonical = (record.englishTitle ?: record.title).trim().takeIf { it.isNotEmpty() } ?: return null
        val titles = record.allTitles()
        // Room persists flattened labels, not a publication-type column. Keep
        // format evidence (including provider tags) through that round-trip.
        val genres = (record.genres + record.tags + listOfNotNull(record.publicationType))
            .map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        val rating = classifier.classify(
            ClassificationInput(
                title = canonical,
                altTitles = titles,
                genres = genres,
                tags = record.tags,
                externalRatings = listOfNotNull(record.contentRating),
                isOfficialPublication = record.isOfficialPublication,
                publicationType = record.publicationType,
                // Adult-only platforms (allporncomic-type scrapers) are explicit evidence
                // even when their tags are sparse or euphemistic.
                sourceId = record.sourceId,
            ),
        )
        // The public feed never receives explicit titles from an uncurated remote feed.
        if (!allowExplicit && rating == ContentRating.EXPLICIT) {
            return null
        }
        val western = WesternComics.matches(
            genres = genres,
            publicationType = record.publicationType,
            publisher = record.publisher,
            titles = titles,
        )
        return Manga(
            id = "${record.sourceId}:${record.remoteId}",
            title = canonical.take(MAX_TITLE_LENGTH),
            altTitles = titles.filterNot { it.equals(canonical, ignoreCase = true) },
            coverUrl = record.coverUrl.httpsOrNull(),
            bannerUrl = record.bannerUrl.httpsOrNull(),
            author = record.author?.trim()?.takeIf { it.isNotEmpty() },
            artist = record.artist?.trim()?.takeIf { it.isNotEmpty() },
            synopsis = record.description?.trim()?.takeIf { it.isNotEmpty() },
            score = record.score?.takeIf { it.isFinite() }?.coerceIn(0.0, 10.0),
            popularity = record.popularity,
            status = record.status,
            genres = if (western) WesternComics.tag(genres) else genres,
            contentRating = rating,
            sourceId = record.sourceId,
            publicationType = record.publicationType,
        )
    }

    private fun String?.httpsOrNull(): String? =
        this?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
}
