package com.yomira.app.catalog

import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.dao.findByAnyTitle
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.database.mapper.toEntity
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import java.util.Locale

/**
 * Merge-before-insert rule that guarantees one card per series on the home grid.
 *
 * A series can reach Room under three different names: the bundled seed stores it
 * with its catalogue title, AniList reports romaji (`Shingeki no Kyojin`) while
 * MangaDex reports English (`Attack on Titan`). Before any network result is
 * inserted, [upsertMerged] looks the series up by canonical title **and** stored
 * alt titles; a hit is merged into the existing row instead of creating a second
 * card for the same story.
 *
 * Merge rules (mirrors the waterfall contract: the first source owns identity):
 *  - the existing row keeps its `id`, `sourceId` and `readingMode`, so chapters,
 *    library entries and reader progress that reference the old id survive;
 *  - the display title is promoted to the incoming (English-first) title while the
 *    previous title demotes to an alt title, keeping every alias searchable;
 *  - volatile facts (artwork, score, status, genres) refresh from the incoming record,
 *    missing text fields fall back to the stored value;
 *  - `contentRating` only ever ratchets upwards — a merge must never demote an adult
 *    title into the public catalog.
 */
object CatalogMerger {
    /** Outcome of [upsertMerged]; useful for telemetry and tests. */
    enum class Outcome { Inserted, Merged }

    /** Outcome plus the row that actually lives in Room after the merge. */
    data class Result(val outcome: Outcome, val entity: MangaEntity)

    suspend fun upsertMerged(mangaDao: MangaDao, incoming: Manga): Outcome =
        upsertMergedAndReturn(mangaDao, incoming).outcome

    /**
     * Same merge-before-insert contract as [upsertMerged], but returns the
     * persisted entity so the paging feed can append the *stored* id (a merged
     * record keeps the original seed id instead of the remote id).
     */
    suspend fun upsertMergedAndReturn(mangaDao: MangaDao, incoming: Manga): Result {
        val conflict = mangaDao.findByAnyTitle(buildList {
            add(incoming.title)
            addAll(incoming.altTitles)
        })
        if (conflict == null) {
            val entity = incoming.toEntity(CatalogSeeder.readingModeFor(incoming.genres))
            mangaDao.upsert(entity)
            return Result(Outcome.Inserted, entity)
        }
        val merged = mergedEntity(conflict, incoming)
        mangaDao.upsert(merged)
        return Result(Outcome.Merged, merged)
    }

    /** Pure merge: existing row wins identity, incoming record refreshes the content. */
    fun mergedEntity(existing: MangaEntity, incoming: Manga): MangaEntity {
        val displayTitle = incoming.title.trim().ifEmpty { existing.title }
        val aliases = buildList {
            addAll(existing.altTitles)
            add(existing.title)
            addAll(incoming.altTitles)
        }.filter { it.isNotBlank() && !it.equals(displayTitle, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
        val genres = (existing.genres + incoming.genres)
            .map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        return existing.copy(
            title = displayTitle,
            altTitles = aliases,
            coverUrl = incoming.coverUrl ?: existing.coverUrl,
            bannerUrl = incoming.bannerUrl ?: existing.bannerUrl,
            author = incoming.author ?: existing.author,
            artist = incoming.artist ?: existing.artist,
            synopsis = incoming.synopsis ?: existing.synopsis,
            score = incoming.score ?: existing.score,
            status = if (incoming.status != MangaStatus.UNKNOWN) incoming.status else existing.status,
            genres = genres,
            contentRating = if (incoming.contentRating.level > existing.contentRating.level) {
                incoming.contentRating
            } else {
                existing.contentRating
            },
            // Identity columns never move: ids are referenced by chapters and by the
            // user's library, and the reading mode is a stored personal preference.
            sourceId = existing.sourceId,
            readingMode = existing.readingMode,
        )
    }
}

/**
 * Title identity helper shared by the trending loader: canonical title first, then
 * every known alias, so a lookup can find `seed:attack-on-titan` through the stored
 * romaji alt title just as well as through the English canonical title.
 */
fun Manga.identityCandidates(): List<String> = buildList {
    add(title)
    addAll(altTitles)
}
