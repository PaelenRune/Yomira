package com.yomira.app.catalog

import com.yomira.core.database.dao.MangaDao
import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSeederTest {

    @Test fun shippedCatalogIsCompleteEnoughToNeverShowAnEmptyHome() {
        val parsed = PrepopulatedCatalogParser.parse(assetPayload())

        assertEquals("Every shipped record must be valid", 0, parsed.skipped)
        assertTrue("Expected at least 100 curated series but was ${parsed.series.size}", parsed.series.size >= 100)

        val ids = parsed.series.map { it.id }
        assertEquals("Seed ids must be unique", ids.size, ids.toSet().size)
        parsed.series.forEach { manga ->
            assertTrue("${manga.id} must use the seed prefix", manga.id.startsWith("seed:"))
            assertNotNull("${manga.id} is missing a cover", manga.coverUrl)
            assertTrue("${manga.id} cover must be https", manga.coverUrl!!.startsWith("https://"))
            assertNotNull("${manga.id} is missing an author", manga.author)
            assertTrue("${manga.id} needs genres", manga.genres.isNotEmpty())
            assertEquals(CatalogSeeder.SEED_SOURCE_ID, manga.sourceId)
            manga.score?.let { score -> assertTrue("${manga.id} score must stay in 0..10", score in 0.0..10.0) }
        }

        // A handful of niche adult entries legitimately ship without a score/synopsis.
        val documented = parsed.series.count { !it.synopsis.isNullOrBlank() }
        val rated = parsed.series.count { it.score != null }
        assertTrue("Synopsis coverage dropped to $documented/${parsed.series.size}", documented >= parsed.series.size * 9 / 10)
        assertTrue("Score coverage dropped to $rated/${parsed.series.size}", rated >= parsed.series.size * 9 / 10)
    }

    @Test fun requiredMainstreamWebtoonWesternAndAdultSeriesArePresent() {
        val all = PrepopulatedCatalogParser.parse(assetPayload()).series
        val required = listOf(
            "One Piece", "Jujutsu Kaisen", "Chainsaw Man", "Berserk", "Attack on Titan",
            "Bleach", "Naruto", "Solo Leveling",
            "Omniscient Reader", "The Beginning After The End", "Tower of God", "Nano Machine",
            "Spider-Man", "Batman", "Saga", "Invincible",
            "MILF Party", "Kimi wa Midara", "Prison School", "Gantz",
            "Silent War", "Boarding Diary", "Secret Class",
        )
        required.forEach { title ->
            assertNotNull("Missing required series: $title", all.findTitle(title))
        }
    }

    @Test fun levelOneStaysMatureAndLevelTwoIsIsolatedAsExplicit() {
        val all = PrepopulatedCatalogParser.parse(assetPayload()).series

        listOf("MILF Party", "Kimi wa Midara", "Prison School", "Gantz").forEach { title ->
            assertEquals("$title must stay on the 16+ home rail", ContentRating.MATURE, all.requireTitle(title).contentRating)
        }
        listOf("Silent War", "Boarding Diary", "Secret Class").forEach { title ->
            assertEquals("$title must live only in the +18 vault", ContentRating.EXPLICIT, all.requireTitle(title).contentRating)
        }
        // Every explicit title must carry a vault filter tag, otherwise it would be unreachable.
        val vaultGenres = setOf("adult webtoons", "hentai manga", "hentai", "smut", "manhwa")
        all.filter { it.contentRating == ContentRating.EXPLICIT }.forEach { manga ->
            assertTrue(
                "${manga.id} needs a vault filter genre",
                manga.genres.any { it.lowercase() in vaultGenres },
            )
        }
    }

    @Test fun readingModeFollowsThePublicationFormat() {
        val all = PrepopulatedCatalogParser.parse(assetPayload()).series

        assertEquals(ReadingMode.WEBTOON, CatalogSeeder.readingModeFor(all.requireTitle("Solo Leveling").genres))
        assertEquals(ReadingMode.WEBTOON, CatalogSeeder.readingModeFor(all.requireTitle("Tower of God").genres))
        assertEquals(ReadingMode.WESTERN_LTR, CatalogSeeder.readingModeFor(all.requireTitle("Batman").genres))
        assertEquals(ReadingMode.MANGA_RTL, CatalogSeeder.readingModeFor(all.requireTitle("Berserk").genres))
    }

    @Test fun storeStatusesAreParsedNotGuessed() {
        val all = PrepopulatedCatalogParser.parse(assetPayload()).series
        assertEquals(MangaStatus.ONGOING, all.requireTitle("One Piece").status)
        assertEquals(MangaStatus.COMPLETED, all.requireTitle("Death Note").status)
        assertEquals(MangaStatus.HIATUS, all.requireTitle("Saga").status)
    }

    @Test fun emptyDatabaseIsSeededOnceFromTheBundledAsset() = runTest {
        val dao = FakeMangaDao()
        val seeder = CatalogSeeder(dao, CatalogAssetReader { assetPayload() }, UnconfinedTestDispatcher(testScheduler))

        val first = seeder.seedIfEmpty()

        assertTrue(first.inserted >= 100)
        assertNull(first.error)
        assertEquals(first.inserted, dao.count())
        assertEquals(dao.count(), dao.rows.count { it.sourceId == CatalogSeeder.SEED_SOURCE_ID })
        assertTrue(dao.rows.any { it.title == "Solo Leveling" })
        assertTrue(dao.rows.first { it.title == "Solo Leveling" }.readingMode == ReadingMode.WEBTOON)

        val second = seeder.seedIfEmpty()
        assertTrue(second.alreadyPopulated)
        assertEquals(0, second.inserted)
        assertEquals(first.inserted, dao.count())
    }

    @Test fun aNonEmptyLibraryIsNeverOverwritten() = runTest {
        val existing = MangaEntity(id = "user:1", title = "My Own Series", sourceId = "user")
        val dao = FakeMangaDao(mutableListOf(existing))
        val seeder = CatalogSeeder(dao, CatalogAssetReader { assetPayload() }, UnconfinedTestDispatcher(testScheduler))

        val result = seeder.seedIfEmpty()

        assertTrue(result.alreadyPopulated)
        assertEquals(1, dao.count())
        assertEquals("My Own Series", dao.rows.single().title)
    }

    @Test fun missingAssetDegradesGracefully() = runTest {
        val dao = FakeMangaDao()
        val seeder = CatalogSeeder(dao, CatalogAssetReader { null }, UnconfinedTestDispatcher(testScheduler))

        val result = seeder.seedIfEmpty()

        assertNotNull(result.error)
        assertEquals(0, result.inserted)
        assertEquals(0, dao.count())
    }

    @Test fun malformedRecordsAreSkippedWhileValidOnesPersist() = runTest {
        val payload = """
            {"catalogVersion":1,"series":[
              {"id":"seed:good","title":"Good","coverUrl":"https://example.org/a.jpg","contentRating":"SAFE","genres":["Action"],"synopsis":"A valid series used by the seeder unit test suite.","author":"Author"},
              {"id":"seed:unknown-rating","title":"Bad rating","contentRating":"WHATEVER"},
              {"id":"not-a-seed-id","title":"Bad id","contentRating":"SAFE"},
              {"id":"seed:good","title":"Duplicate","contentRating":"SAFE"},
              {"id":"seed:bad-url","title":"Bad cover","coverUrl":"http://insecure.example/a.jpg","contentRating":"SAFE"}
            ]}
        """.trimIndent()
        val dao = FakeMangaDao()
        val seeder = CatalogSeeder(dao, CatalogAssetReader { payload }, UnconfinedTestDispatcher(testScheduler))

        val result = seeder.seedIfEmpty()

        assertEquals(2, result.inserted)
        assertEquals(3, result.skipped)
        assertEquals(setOf("seed:good", "seed:bad-url"), dao.rows.map { it.id }.toSet())
        assertEquals("https://example.org/a.jpg", dao.rows.first { it.id == "seed:good" }.coverUrl)
        // A cleartext cover is dropped instead of being loaded by Coil.
        assertNull(dao.rows.first { it.id == "seed:bad-url" }.coverUrl)
    }

    private fun List<Manga>.findTitle(query: String): Manga? =
        firstOrNull { it.title.equals(query, ignoreCase = true) }
            ?: firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: firstOrNull { manga -> manga.altTitles.any { it.contains(query, ignoreCase = true) } }

    private fun List<Manga>.requireTitle(query: String): Manga {
        val match = findTitle(query)
        assertNotNull("Missing required series: $query", match)
        return match!!
    }

    private fun assetPayload(): String {
        val file = candidateAssetFiles().firstOrNull { it.isFile }
            ?: error("prepopulated_catalog.json is missing from the app assets")
        return file.readText(Charsets.UTF_8)
    }

    private fun candidateAssetFiles(): List<File> = listOf(
        File("src/main/assets/${CatalogSeeder.ASSET_NAME}"),
        File("app/src/main/assets/${CatalogSeeder.ASSET_NAME}"),
    )

    private class FakeMangaDao(
        val rows: MutableList<MangaEntity> = mutableListOf(),
    ) : MangaDao {
        override suspend fun updateDisplayMetadata(id: String, title: String, aliases: List<String>, cover: String?) = Unit
        override suspend fun updateContentRating(mangaId: String, rating: ContentRating) {
            val index = rows.indexOfFirst { it.id == mangaId }
            if (index >= 0) rows[index] = rows[index].copy(contentRating = rating)
        }

        override suspend fun updateReadingMode(mangaId: String, mode: ReadingMode) {
            val index = rows.indexOfFirst { it.id == mangaId }
            if (index >= 0) rows[index] = rows[index].copy(readingMode = mode)
        }

        override fun observeRecentlyUpdated(): Flow<List<MangaEntity>> = flowOf(rows.toList())
        override fun observeAll(): Flow<List<MangaEntity>> = flowOf(rows.toList())
        override fun observeById(id: String): Flow<MangaEntity?> = flowOf(rows.firstOrNull { it.id == id })
        override fun observeBySource(sourceId: String): Flow<List<MangaEntity>> = flowOf(rows.filter { it.sourceId == sourceId })
        override suspend fun getById(id: String): MangaEntity? = rows.firstOrNull { it.id == id }
        override suspend fun count(): Int = rows.size
        override suspend fun existsByTitle(title: String): Boolean = rows.any { it.title.equals(title, ignoreCase = true) }
        override suspend fun findByTitleOrPattern(title: String, elementPattern: String): MangaEntity? =
            rows.firstOrNull { stored ->
                stored.title.equals(title, ignoreCase = true) ||
                    stored.altTitles.any { it.equals(title, ignoreCase = true) }
            }
        override fun observeByRawQuery(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<MangaEntity>> =
            flowOf(rows.toList())
        override suspend fun upsert(manga: MangaEntity) {
            rows.removeAll { it.id == manga.id }
            rows += manga
        }
        override suspend fun upsertAll(manga: List<MangaEntity>) { manga.forEach { upsert(it) } }
        override suspend fun deleteById(id: String) { rows.removeAll { it.id == id } }
    }
}
