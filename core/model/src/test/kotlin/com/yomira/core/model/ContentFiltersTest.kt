package com.yomira.core.model

import org.junit.Assert.*
import org.junit.Test

class ContentFiltersTest {
    private fun manga(vararg tags: String, rating: ContentRating = ContentRating.SAFE) =
        Manga("id", "Title", genres = tags.toList(), contentRating = rating, sourceId = "test")

    @Test fun `blacklist recognizes source aliases and punctuation`() {
        mapOf(
            "yaoi" to listOf("Yaoi", "BL", "Boys' Love", "Shōnen-ai"),
            "yuri" to listOf("GL", "Girls Love", "Shoujo Ai"),
            "futanari" to listOf("Futanari", "Futa"),
        ).forEach { (key, aliases) -> aliases.forEach { assertTrue("$key / $it", manga(it).hasExcludedTags(setOf(key))) } }
    }

    @Test fun `hard aliases hide while style aliases only penalize`() {
        listOf("futanari" to "Futa", "incest" to "Incest", "beastiality" to "Bestiality", "rape" to "Non-con", "loli" to "Lolicon", "shota" to "Shotacon").forEach { (key, label) ->
            assertTrue(label, manga(label).hasExcludedTags(setOf(key)))
        }
        listOf("harem" to "Reverse Harem", "ntr" to "Netorare", "gore" to "Guro", "ecchi" to "Etchi", "gore" to "Graphic Violence").forEach { (key, label) ->
            val item = manga(label)
            assertFalse(label, item.hasExcludedTags(setOf(key)))
            assertTrue(label, item.stylePenalty(setOf(key)) > 0)
        }
        listOf("Drama", "Romance", "Supernatural").forEach { label ->
            assertFalse(label, manga(label).hasExcludedTags(setOf("futanari", "incest", "rape", "loli", "shota")))
        }
    }

    @Test fun `hard core is independent of preferences and rating`() {
        listOf("Futa", "Incest", "Bestiality", "Non-con", "Lolicon", "Shotacon").forEach { label ->
            ContentRating.entries.forEach { rating ->
                listOf(emptySet(), setOf("harem"), setOf("futanari", "yaoi", "ntr")).forEach { preferences ->
                    assertFalse("$label $rating $preferences", manga(label, rating = rating).isVisibleForReader(preferences, true, true))
                }
            }
        }
    }

    @Test fun `blacklist wins regardless of age or adult consent`() {
        ContentRating.entries.forEach { rating ->
            assertFalse(manga("Futanari", rating = rating).isVisibleForReader(setOf("futanari"), true, true))
        }
    }

    @Test fun `age gate and explicit opt in are independent`() {
        assertTrue(manga(rating = ContentRating.SAFE).isVisibleForReader(emptySet(), false, false))
        assertFalse(manga(rating = ContentRating.MATURE).isVisibleForReader(emptySet(), false, true))
        assertTrue(manga(rating = ContentRating.MATURE).isVisibleForReader(emptySet(), true, false))
        assertFalse(manga(rating = ContentRating.EXPLICIT).isVisibleForReader(emptySet(), false, true))
        assertFalse(manga(rating = ContentRating.EXPLICIT).isVisibleForReader(emptySet(), true, false))
        assertTrue(manga(rating = ContentRating.EXPLICIT).isVisibleForReader(emptySet(), true, true))
    }

    @Test fun `short aliases do not match parts of unrelated words`() {
        assertFalse(manga("Goblin", "Supernatural", "Girls Club").hasExcludedTags(setOf("yaoi", "yuri", "ntr")))
        assertTrue(manga("Space Opera").hasExcludedTags(setOf("space-opera")))
    }
}
