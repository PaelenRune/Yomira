package com.yomira.feature.home

import com.yomira.core.model.ContentRating
import com.yomira.core.model.HomeShelf
import com.yomira.core.model.Manga
import com.yomira.domain.extension.FavoriteSeries
import org.junit.Assert.*
import org.junit.Test

class FavoriteSeriesTest {
    private fun manga(id: String, genre: String = "Fantasy") = Manga(
        id = id, title = "Series $id", sourceId = "test", genres = listOf(genre),
        contentRating = ContentRating.SAFE, score = 7.0,
    )

    @Test fun `local search matches titles aliases and genres without case sensitivity`() {
        val item = manga("one").copy(altTitles = listOf("Alternate"))
        val catalog = listOf(item, manga("two", "Romance"))
        assertEquals(listOf(item), searchLocalCatalog(catalog, "SERIES ONE"))
        assertEquals(listOf(item), searchLocalCatalog(catalog, "alternate"))
        assertEquals(listOf(item), searchLocalCatalog(catalog, "fantasy"))
        assertTrue(searchLocalCatalog(catalog, "missing").isEmpty())
    }

    @Test fun `selection stops at five removes and allows a replacement`() {
        val items = (1..6).map { FavoriteSeries("$it", "Title $it") }
        val selected = items.fold(emptyList<FavoriteSeries>(), ::toggleFavoriteSeries)
        assertEquals(items.take(5), selected)
        val removed = toggleFavoriteSeries(selected, items.first())
        assertEquals(4, removed.size)
        assertEquals(5, toggleFavoriteSeries(removed, items.last()).size)
    }

    @Test fun `saved selection survives recreation including title only entries`() {
        val selected = listOf(FavoriteSeries("one", "Türkçe"), FavoriteSeries(title = "Title only"))
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val saved = with(FavoriteSeriesSaver) { scope.save(selected) }!!
        assertEquals(selected, FavoriteSeriesSaver.restore(saved))
    }

    @Test fun `favorite seeds resolve id or title and supersede legacy genres`() {
        val item = manga("one").copy(altTitles = listOf("Alias"), genres = listOf(" Fantasy ", "Action"))
        val favorites = listOf(FavoriteSeries(item.id, "Old title"), FavoriteSeries(title = "ALIAS"))
        assertEquals(mapOf("fantasy" to 1, "action" to 1),
            favoriteSeriesGenreAffinity(listOf(item, item), favorites, setOf("Romance")))
        assertEquals(mapOf("romance" to 1), favoriteSeriesGenreAffinity(listOf(item), emptyList(), setOf(" Romance ")))
        assertTrue(favoriteSeriesGenreAffinity(listOf(item), listOf(FavoriteSeries("missing", "unknown")), setOf("Romance")).isEmpty())
    }

    @Test fun `seeded and legacy rails favor matching unread series deterministically`() {
        val seed = manga("seed").copy(score = 1.0)
        val match = manga("match")
        val other = manga("other", "Romance").copy(score = 9.0)
        val catalog = listOf(seed, match, other)
        fun rail(items: List<Manga>, favorites: List<FavoriteSeries>, legacy: Set<String>) = buildHomeRails(
            items, emptyList(), emptyList(), selection = listOf(HomeShelf.PERSONAL),
            readIds = emptySet(), savedIds = emptySet(), seed = 1,
            favoriteSeries = favorites, legacyFavoriteGenres = legacy,
        ).single().items
        val favorites = listOf(FavoriteSeries(seed.id, seed.title))
        assertEquals(match, rail(catalog, favorites, emptySet()).first())
        assertEquals(rail(catalog, favorites, emptySet()), rail(catalog.reversed(), favorites, emptySet()))
        assertEquals(other, rail(catalog, emptyList(), setOf("Romance")).first())
    }
}
