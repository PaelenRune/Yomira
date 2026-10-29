package com.yomira.data.metadata

import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.network.MetadataProvider
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.ProviderFailure
import com.yomira.core.network.RemoteMetadata
import com.yomira.domain.classifier.ClassificationInput
import com.yomira.domain.classifier.ContentRatingClassifier
import com.yomira.domain.extension.TitleMatcher
import com.yomira.domain.extension.TitleNormalizer
import java.time.Clock
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface MetadataCache {
    suspend fun read(key: String, nowMillis: Long): String?
    suspend fun write(key: String, payload: String, nowMillis: Long, expiresAtMillis: Long)
    suspend fun pruneExpired(nowMillis: Long)
}

/**
 * First source owns identity; later sources fill holes, never replace established fields.
 *
 * The produced [Manga.title] is the display title and always prefers the English name
 * (`RemoteMetadata.englishTitle`) so romaji-only records such as *Shingeki no Kyojin*
 * are shelved as *Attack on Titan*. Every non-display title survives as an alt title,
 * which keeps alias-based duplicate detection intact.
 */
class WaterfallMetadataAggregator(
    providers: List<MetadataProvider>,
    private val cache: MetadataCache,
    private val classifier: ContentRatingClassifier = ContentRatingClassifier(),
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofHours(24),
    private val onFailure: (ProviderFailure) -> Unit = {},
    private val aliasSink: SeriesAliasSink? = null,
) {
    private val order = listOf(MetadataSources.BAKA_UPDATES, MetadataSources.ANILIST,
        MetadataSources.MANGADEX, MetadataSources.KITSU, MetadataSources.COMICVINE,
        MetadataSources.OPENLIBRARY)
    private val providers = providers.sortedBy { order.indexOf(it.sourceId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    private val json = Json { ignoreUnknownKeys = true }

    init { require(!ttl.isNegative && !ttl.isZero) { "Cache TTL must be positive" } }

    suspend fun aggregate(query: String, forceRefresh: Boolean = false): Manga? {
        val key = normalize(query)
        require(key.isNotBlank()) { "Search query must not be blank" }
        val now = clock.millis()
        if (!forceRefresh) {
            val cached = guarded("metadata-cache") {
                cache.read(key, now)?.let { json.decodeFromString<List<RemoteMetadata>>(it) }
            }
            if (!cached.isNullOrEmpty()) {
                recordAliases(distinctRecords(cached))
                return merge(distinctRecords(cached))
            }
        }
        val records = mutableListOf<RemoteMetadata>()
        for (provider in providers) {
            if (!provider.isAvailable) continue
            val results = guarded(provider.sourceId) { provider.search(query, 5) }.orEmpty()
            val accepted = TitleMatcher.Subject(
                records.flatMap { it.allTitles() } + query,
                year = records.firstOrNull()?.year,
                genres = records.flatMap { it.genres }.toSet(),
            )
            val anchorYear = records.firstOrNull()?.year
            val match = results.firstOrNull { candidate ->
                // Shared scoring across search paths: the waterfall accepts a
                // provider record exactly when the unified matcher would call
                // it the same series, with year/genre evidence breaking ties.
                val evidence = TitleMatcher.Evidence(
                    year = candidate.year,
                    genres = (candidate.genres + candidate.tags).toSet(),
                )
                candidate.allTitles().any { title -> TitleMatcher.matches(title, accepted, evidence) } &&
                    (anchorYear == null || candidate.year == null || anchorYear == candidate.year)
            }
            // Same remote id from the same provider can never be merged twice, even
            // from a cache written by an older app version.
            if (match != null && records.none { it.sourceId == match.sourceId && it.remoteId == match.remoteId }) {
                records += match
            }
        }
        if (records.isEmpty()) return null
        // Sweep all keys on writes so expired, never-read-again queries cannot accumulate.
        // Keep cleanup isolated: a failed sweep must not prevent caching fresh metadata.
        guarded("metadata-cache") { cache.pruneExpired(now) }
        guarded("metadata-cache") { cache.write(key, json.encodeToString(records), now, Math.addExact(now, ttl.toMillis())) }
        recordAliases(distinctRecords(records))
        return merge(distinctRecords(records))
    }

    /**
     * Feeds every observed title to the alias dictionary sink. Pure evidence
     * for matching: never influences the merged display title, which
     * [TitleLocalizer] owns. A failing sink must not break aggregation.
     */
    private suspend fun recordAliases(records: List<RemoteMetadata>) {
        val sink = aliasSink ?: return
        val aliases = records.flatMap { record ->
            val seriesId = "${record.sourceId}:${record.remoteId}"
            buildList {
                add(SeriesAlias(seriesId, record.title, source = record.sourceId))
                record.englishTitle?.let { add(SeriesAlias(seriesId, it, "en", record.sourceId)) }
                record.altTitles.forEach { (lang, titles) ->
                    titles.forEach { add(SeriesAlias(seriesId, it, lang, record.sourceId)) }
                }
            }
        }.filter { it.title.isNotBlank() }
        if (aliases.isEmpty()) return
        try {
            sink.record(aliases)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onFailure(ProviderFailure("alias-sink", "Alias recording failed"))
        }
    }

    /** Drops exact duplicate provider records so one series never merges into two cards. */
    private fun distinctRecords(records: List<RemoteMetadata>): List<RemoteMetadata> =
        records.distinctBy { "${it.sourceId}:${it.remoteId}" }

    private fun merge(records: List<RemoteMetadata>): Manga {
        val first = records.first()
        fun text(select: (RemoteMetadata) -> String?) = records.firstNotNullOfOrNull { select(it)?.takeIf(String::isNotBlank) }
        val genres = records.flatMap { it.genres + it.tags + listOfNotNull(it.publicationType) }
            .distinctBy { it.lowercase(Locale.ROOT) }
        // English display title wins: `Shingeki no Kyojin` is shelved as `Attack on Titan`,
        // `Boku no Hero Academia` as `My Hero Academia`. The romaji survives as an alt title,
        // so search and cross-provider identity matching keep working.
        val displayTitle = TitleLocalizer.select(records) ?: first.title
        val titles = records.flatMap { it.allTitles() }.distinctBy(TitleNormalizer::canonical)
        return Manga(
            id = "${first.sourceId}:${first.remoteId}", title = displayTitle,
            altTitles = titles.filterNot { TitleNormalizer.canonical(it) == TitleNormalizer.canonical(displayTitle) },
            coverUrl = ArtworkResolver.cover(records), bannerUrl = ArtworkResolver.backdrop(records),
            author = text { it.author }, artist = text { it.artist }, synopsis = text { it.description },
            score = records.firstNotNullOfOrNull { it.score },
            popularity = records.firstNotNullOfOrNull { it.popularity },
            status = records.firstOrNull { it.status != MangaStatus.UNKNOWN }?.status ?: MangaStatus.UNKNOWN,
            genres = genres,
            contentRating = classifier.classify(ClassificationInput(
                title = displayTitle, altTitles = titles, genres = genres,
                tags = records.flatMap { it.tags }, externalRatings = records.mapNotNull { it.contentRating },
                isOfficialPublication = records.firstNotNullOfOrNull { it.isOfficialPublication },
                publicationType = text { it.publicationType },
            )),
            sourceId = first.sourceId,
            publicationType = text { it.publicationType },
        )
    }

    private suspend fun <T> guarded(source: String, block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Do not expose HTTP URLs containing credentials through UI diagnostics.
        onFailure(ProviderFailure(source, "Metadata operation failed"))
        null
    }

    /** Cache key: the unified engine's fold, so `One Piece` and `one piece` share an entry. */
    private fun normalize(value: String) = TitleNormalizer.canonical(value)
}
