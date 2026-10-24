package com.yomira.app.catalog

import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.findByAnyTitle
import com.yomira.core.database.mapper.toEntity
import com.yomira.core.model.Manga
import com.yomira.core.network.AniListClient
import com.yomira.core.network.MangaDexClient
import com.yomira.core.network.RemoteMetadata
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
 *
 * Titles follow the English-first display rule: `englishTitle` (Attack on Titan)
 * beats provider romaji (Shingeki no Kyojin). Nothing is inserted blindly — every
 * record first goes through [CatalogMerger] so a series that is already in the
 * database under another language name is updated, never duplicated.
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

        val seenIdentities = HashSet<String>()
        val mergedIds = HashSet<String>()
        var written = 0
        for (record in records) {
            val manga = record.toMangaOrNull() ?: continue
            // Cross-language de-duplication: the same trending series can arrive from
            // AniList under one English title and from MangaDex under a slight variant.
            if (!seenIdentities.add(manga.identityCandidates().joinToString("|") { it.lowercase(Locale.ROOT) })) continue
            if (mangaDao.getById(manga.id) != null) continue
            val conflict = mangaDao.findByAnyTitle(manga.identityCandidates())
            when {
                conflict == null -> {
                    mangaDao.upsert(manga.toEntity(CatalogSeeder.readingModeFor(manga.genres)))
                    written++
                }
                // A seed row holding the Japanese/romaji name absorbs the trending
                // record under its original id; a second card never reaches the grid.
                mergedIds.add(conflict.id) -> {
                    mangaDao.upsert(CatalogMerger.mergedEntity(conflict, manga))
                    written++
                }
            }
        }
        written
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

    private fun RemoteMetadata.toMangaOrNull(): Manga? =
        // Shared with the paging engine: same English-title-first rule and the
        // same western-comics genre marker for trending records.
        CatalogRecordMapper.toMangaOrNull(this, classifier, allowExplicit = false)

    companion object {
        const val DEFAULT_LIMIT = 24
    }
}
