package com.yomira.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.yomira.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    /** Display-only migration: never replace reading state, identity or safety classification. */
    @Query("UPDATE manga SET title = :title, altTitles = :aliases, coverUrl = COALESCE(:cover, coverUrl) WHERE id = :id")
    suspend fun updateDisplayMetadata(id: String, title: String, aliases: List<String>, cover: String?)

    @Query("UPDATE manga SET contentRating = :rating WHERE id = :mangaId")
    suspend fun updateContentRating(mangaId: String, rating: com.yomira.core.model.ContentRating)

    @Query("UPDATE manga SET readingMode = :mode WHERE id = :mangaId")
    suspend fun updateReadingMode(mangaId: String, mode: com.yomira.core.model.ReadingMode)

    @Query("SELECT manga.* FROM manga LEFT JOIN chapters ON manga.id = chapters.mangaId GROUP BY manga.id ORDER BY MAX(chapters.releaseDate) DESC, manga.id")
    fun observeRecentlyUpdated(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga ORDER BY title COLLATE NOCASE, id")
    fun observeAll(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun observeById(id: String): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun getById(id: String): MangaEntity?

    @Query("SELECT COUNT(*) FROM manga")
    suspend fun count(): Int

    /** Used by background sync so a seeded series is never duplicated by a remote id. */
    @Query("SELECT EXISTS(SELECT 1 FROM manga WHERE title = :title COLLATE NOCASE)")
    suspend fun existsByTitle(title: String): Boolean

    /**
     * One candidate row whose canonical title equals [title] (case-insensitive) or whose
     * serialised [MangaEntity.altTitles] JSON contains it as a whole element. Powers the
     * merge-before-insert rule: a series already stored under its Japanese or romaji name
     * must be merged into, never re-inserted under its English name.
     */
    @Query("""
        SELECT * FROM manga
        WHERE title = :title COLLATE NOCASE
           OR altTitles LIKE :elementPattern ESCAPE '\'
        ORDER BY id
        LIMIT 1
    """)
    suspend fun findByTitleOrPattern(title: String, elementPattern: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId ORDER BY title COLLATE NOCASE, id")
    fun observeBySource(sourceId: String): Flow<List<MangaEntity>>

    /** Dynamic genre/tag matrix entry point; see the `observeByGenres` extension builder. */
    @RawQuery(observedEntities = [MangaEntity::class])
    fun observeByRawQuery(query: SupportSQLiteQuery): Flow<List<MangaEntity>>

    // Unlike INSERT OR REPLACE, UPSERT does not delete the parent and cascade to chapters.
    @Upsert
    suspend fun upsert(manga: MangaEntity)

    @Upsert
    suspend fun upsertAll(manga: List<MangaEntity>)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * Serialises one string exactly the way [DatabaseConverters] stores list elements and
 * wraps it as a `LIKE` pattern that only matches the element as a whole JSON entry
 * (`"Action"` never matches `"Action & Adventure"`). `%`, `_` and `\` inside the value
 * are escaped for both the JSON layer and the SQLite `ESCAPE '\'` layer.
 */
internal fun jsonElementLikePattern(value: String): String {
    val jsonEscaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    val likeEscaped = jsonEscaped.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%\"$likeEscaped\"%"
}

/**
 * Dynamic multi-genre filter over the JSON `genres` column: returns every series that
 * carries **at least one** of the requested genres (union semantics, matching the tag
 * bar). An empty list degrades to [observeAll] so callers never special-case "no filter".
 *
 * `LIKE ... COLLATE NOCASE` is ASCII-only case folding — exactly what the persisted
 * provider genres ("Action", "Comedy", "Ecchi") need. Values are bound, never inlined,
 * so quoting can never alter the statement.
 */
fun MangaDao.observeByGenres(genres: List<String>): Flow<List<MangaEntity>> {
    val terms = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    if (terms.isEmpty()) return observeAll()
    val bindArgs = ArrayList<Any>(terms.size)
    val clauses = terms.joinToString(separator = " OR ") {
        bindArgs += jsonElementLikePattern(it)
        // SQLite LIKE already folds ASCII case; the JSON quotes keep whole-element matches.
        "genres LIKE ? ESCAPE '\\'"
    }
    val sql = "SELECT * FROM manga WHERE $clauses ORDER BY title COLLATE NOCASE, id"
    return observeByRawQuery(SimpleSQLiteQuery(sql, bindArgs.toTypedArray()))
}

/**
 * Finds the row that already represents any of [titles] — canonical title or stored alt
 * title. Used by the catalog merger so `Attack on Titan` fetched from AniList updates the
 * existing `Shingeki no Kyojin` row instead of creating a second card on the home grid.
 */
suspend fun MangaDao.findByAnyTitle(titles: List<String>): MangaEntity? {
    val candidates = titles.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    for (candidate in candidates) {
        findByTitleOrPattern(candidate, jsonElementLikePattern(candidate))?.let { return it }
    }
    return null
}
