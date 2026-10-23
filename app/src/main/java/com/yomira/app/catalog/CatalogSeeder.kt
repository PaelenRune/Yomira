package com.yomira.app.catalog

import android.content.Context
import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the bundled catalogue from `assets/` so seeding works on devices that
 * have never been online. Kept as an interface so the seeder is unit-testable
 * without Robolectric.
 */
fun interface CatalogAssetReader {
    suspend fun read(assetName: String): String?
}

class AndroidCatalogAssetReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : CatalogAssetReader {
    override suspend fun read(assetName: String): String? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: IOException) {
            // A missing or unreadable asset must degrade to an empty catalogue, never crash launch.
            null
        }
    }
}

/** Wire format of the shipped catalogue. Keep field names in sync with the JSON asset. */
@Serializable
internal data class PrepopulatedCatalog(
    val catalogVersion: Int = 1,
    val defaultSourceId: String = CatalogSeeder.SEED_SOURCE_ID,
    val series: List<PrepopulatedSeries> = emptyList(),
)

@Serializable
internal data class PrepopulatedSeries(
    val id: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val synopsis: String? = null,
    val score: Double? = null,
    val status: String = "UNKNOWN",
    val genres: List<String> = emptyList(),
    val contentRating: String = "SAFE",
    val sourceId: String = CatalogSeeder.SEED_SOURCE_ID,
)

data class ParsedCatalog(val series: List<Manga>, val skipped: Int)

/**
 * Turns the shipped JSON into domain models.
 *
 * Every record is validated independently: one malformed entry is counted and
 * dropped instead of aborting the whole bootstrap. An unknown `contentRating`
 * is treated as malformed rather than guessed, because guessing "safe" would
 * leak adult titles into the public grid.
 */
object PrepopulatedCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val seedIdPattern = Regex("^seed:[a-z0-9-]+$")

    fun parse(payload: String): ParsedCatalog {
        require(payload.isNotBlank()) { "Catalog payload must not be blank" }
        val decoded = json.decodeFromString<PrepopulatedCatalog>(payload)
        var skipped = 0
        val seen = HashSet<String>()
        val series = decoded.series.mapNotNull { record ->
            val manga = record.toMangaOrNull()
            if (manga == null || !seen.add(manga.id)) {
                skipped++
                null
            } else {
                manga
            }
        }
        return ParsedCatalog(series = series, skipped = skipped)
    }

    private fun PrepopulatedSeries.toMangaOrNull(): Manga? {
        val canonicalTitle = title.trim().take(MAX_TITLE_LENGTH)
        if (canonicalTitle.isEmpty()) return null
        if (!seedIdPattern.matches(id)) return null
        val rating = when (contentRating.trim().uppercase()) {
            ContentRating.SAFE.name -> ContentRating.SAFE
            ContentRating.MATURE.name -> ContentRating.MATURE
            ContentRating.EXPLICIT.name -> ContentRating.EXPLICIT
            else -> return null
        }
        val normalizedScore = score?.takeIf { it.isFinite() && it in 0.0..10.0 }
        return Manga(
            id = id,
            title = canonicalTitle,
            altTitles = altTitles.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            coverUrl = coverUrl.secureImageUrl(),
            bannerUrl = bannerUrl.secureImageUrl(),
            author = author.cleanText(),
            artist = artist.cleanText(),
            synopsis = synopsis.cleanText(),
            score = normalizedScore,
            status = status.toMangaStatus(),
            genres = genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            contentRating = rating,
            sourceId = CatalogSeeder.SEED_SOURCE_ID,
        )
    }

    /** Only https artwork is accepted; the app never loads cleartext or file URIs for covers. */
    private fun String?.secureImageUrl(): String? {
        val value = this?.trim().orEmpty()
        return value.takeIf { it.startsWith("https://", ignoreCase = true) && it.length <= MAX_URL_LENGTH }
    }

    private fun String?.cleanText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.toMangaStatus(): MangaStatus =
        MangaStatus.entries.firstOrNull { it.name == trim().uppercase() } ?: MangaStatus.UNKNOWN

    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_URL_LENGTH = 2_048
}

data class SeedResult(
    val inserted: Int = 0,
    val skipped: Int = 0,
    /** True when the database already had rows, so seeding was intentionally skipped. */
    val alreadyPopulated: Boolean = false,
    val error: String? = null,
)

/**
 * Bootstraps Room from the bundled catalogue the first time the app runs.
 *
 * Seeding only happens against an empty database: re-running it on every launch
 * would resurrect titles the user deleted and would overwrite user edits.
 */
class CatalogSeeder @Inject constructor(
    private val mangaDao: MangaDao,
    private val assetReader: CatalogAssetReader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun seedIfEmpty(): SeedResult = withContext(ioDispatcher) {
        val existing = try {
            mangaDao.count()
        } catch (error: Exception) {
            return@withContext SeedResult(error = error.message ?: "Katalog durumu okunamadı")
        }
        if (existing > 0) return@withContext SeedResult(alreadyPopulated = true)

        val payload = try {
            assetReader.read(ASSET_NAME)
        } catch (error: Exception) {
            return@withContext SeedResult(error = error.message ?: "Katalog dosyası okunamadı")
        }
        if (payload.isNullOrBlank()) {
            return@withContext SeedResult(error = "Katalog dosyası bulunamadı: $ASSET_NAME")
        }

        val parsed = try {
            PrepopulatedCatalogParser.parse(payload)
        } catch (error: Exception) {
            return@withContext SeedResult(error = error.message ?: "Katalog dosyası geçersiz")
        }
        if (parsed.series.isEmpty()) {
            return@withContext SeedResult(skipped = parsed.skipped, error = "Katalog dosyasında geçerli seri yok")
        }

        val entities = parsed.series.map { it.toSeedEntity() }
        try {
            mangaDao.upsertAll(entities)
        } catch (error: Exception) {
            return@withContext SeedResult(skipped = parsed.skipped, error = error.message ?: "Katalog yazılamadı")
        }
        SeedResult(inserted = entities.size, skipped = parsed.skipped)
    }

    companion object {
        const val ASSET_NAME = "prepopulated_catalog.json"
        const val SEED_SOURCE_ID = "seed"

        /** Webtoons scroll vertically; western comics read left-to-right; everything else is manga. */
        internal fun readingModeFor(genres: List<String>): ReadingMode {
            val normalized = genres.map { it.trim().lowercase() }
            return when {
                normalized.any {
                    it == "webtoon" || it == "webtoons" || it == "manhwa" || it == "manhua" ||
                        it == "adult webtoons"
                } -> ReadingMode.WEBTOON
                normalized.any { it == "comic" || it == "western comic" || it == "graphic novel" } ->
                    ReadingMode.WESTERN_LTR
                else -> ReadingMode.MANGA_RTL
            }
        }
    }
}

internal fun Manga.toSeedEntity(): MangaEntity = MangaEntity(
    id = id,
    title = title,
    altTitles = altTitles,
    coverUrl = coverUrl,
    bannerUrl = bannerUrl,
    author = author,
    artist = artist,
    synopsis = synopsis,
    score = score,
    status = status,
    genres = genres,
    contentRating = contentRating,
    sourceId = sourceId,
    readingMode = CatalogSeeder.readingModeFor(genres),
)
