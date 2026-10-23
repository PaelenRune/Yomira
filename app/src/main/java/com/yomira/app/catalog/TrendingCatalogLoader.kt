package com.yomira.app.catalog

import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.database.mapper.toEntity
import com.yomira.core.model.ContentRating
import com.yomira.core.model.ExternalRating
import com.yomira.core.model.Manga
import com.yomira.core.network.AniListClient
import com.yomira.core.network.MangaDexClient
import com.yomira.core.network.RemoteMetadata
import com.yomira.domain.classifier.ClassificationInput
import com.yomira.domain.classifier.ContentRatingClassifier
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Warms the local catalogue with trending titles when the device is online.
 *
 * The network is best-effort: a failure is silent because the bundled seed
 * catalogue already guarantees a non-empty home screen. Results are stored
 * with their real provider source id so the waterfall can refresh them later.
 */
class TrendingCatalogLoader @Inject constructor(
    private val aniListClient: AniListClient,
    private val mangaDexClient: MangaDexClient,
    private val mangaDao: MangaDao,
    private val classifier: ContentRatingClassifier,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun refresh(limit: Int = DEFAULT_LIMIT): Int = withContext(ioDispatcher) {
        val records = fetch(limit)
        if (records.isEmpty()) return@withContext 0

        val seenTitles = HashSet<String>()
        val entities = ArrayList<MangaEntity>(records.size)
        for (record in records) {
            val manga = record.toMangaOrNull() ?: continue
            if (!seenTitles.add(manga.title.lowercase(Locale.ROOT))) continue
            if (mangaDao.existsByTitle(manga.title)) continue
            entities += manga.toEntity(CatalogSeeder.readingModeFor(manga.genres))
        }
        if (entities.isEmpty()) return@withContext 0
        mangaDao.upsertAll(entities)
        entities.size
    }

    private suspend fun fetch(limit: Int): List<RemoteMetadata> = coroutineScope {
        val aniList = async { guarded { aniListClient.trending(limit) } }
        val mangaDex = async { guarded { mangaDexClient.trending(limit) } }
        // AniList artwork is usually richer, so its records win the later title de-duplication.
        aniList.await() + mangaDex.await()
    }

    private suspend fun guarded(block: suspend () -> List<RemoteMetadata>): List<RemoteMetadata> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }

    private fun RemoteMetadata.toMangaOrNull(): Manga? {
        val canonical = title.trim().takeIf { it.isNotEmpty() } ?: return null
        val rating = classifier.classify(
            ClassificationInput(
                title = canonical,
                altTitles = allTitles(),
                genres = genres,
                tags = tags,
                externalRatings = listOfNotNull(contentRating),
                isOfficialPublication = isOfficialPublication,
                publicationType = publicationType,
            ),
        )
        // The public feed never receives explicit titles from an uncurated remote feed.
        if (rating == ContentRating.EXPLICIT || contentRating == ExternalRating.PORNOGRAPHIC) return null
        return Manga(
            id = "$sourceId:$remoteId",
            title = canonical.take(MAX_TITLE_LENGTH),
            altTitles = allTitles().filterNot { it.equals(canonical, ignoreCase = true) },
            coverUrl = coverUrl.httpsOrNull(),
            bannerUrl = bannerUrl.httpsOrNull(),
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            artist = artist?.trim()?.takeIf { it.isNotEmpty() },
            synopsis = description?.trim()?.takeIf { it.isNotEmpty() },
            score = score?.takeIf { it.isFinite() }?.coerceIn(0.0, 10.0),
            status = status,
            genres = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            contentRating = rating,
            sourceId = sourceId,
        )
    }

    private fun String?.httpsOrNull(): String? =
        this?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }

    companion object {
        const val DEFAULT_LIMIT = 24
        private const val MAX_TITLE_LENGTH = 200
    }
}
