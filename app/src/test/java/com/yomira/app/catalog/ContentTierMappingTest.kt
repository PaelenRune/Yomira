package com.yomira.app.catalog

import com.yomira.core.model.ContentRating
import com.yomira.core.model.ExternalRating
import com.yomira.core.network.aniListContentRating
import com.yomira.core.network.kitsuContentRating
import com.yomira.domain.classifier.ClassificationInput
import com.yomira.domain.classifier.ContentRatingClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v8.2.0 tier-model mapping tests: every provider signal collapses into a
 * content tier through one shared mapping layer
 * ([ExternalRating.tier] + [ExternalRating.strongestTier]), provider adapters
 * normalise their raw payloads into that evidence, and the classifier combines
 * them with the cautious-upper-tier rule on conflicts.
 */
class ContentTierMappingTest {
    private val classifier = ContentRatingClassifier()

    // --- The shared evidence → tier mapping ---------------------------------

    @Test fun `every external rating signal maps to its tier`() {
        assertEquals(ContentRating.SAFE, ExternalRating.SAFE.tier)
        assertEquals(ContentRating.MATURE, ExternalRating.SUGGESTIVE.tier)
        assertEquals(ContentRating.MATURE, ExternalRating.EROTICA.tier)
        assertEquals(ContentRating.EXPLICIT, ExternalRating.PORNOGRAPHIC.tier)
    }

    @Test fun `strongest tier wins on conflicting provider signals`() {
        // The cautious upper tier always wins; a safe label never softens a
        // pornographic one, and mature evidence outranks a safe one.
        assertNull(ExternalRating.strongestTier(emptyList()))
        assertEquals(ContentRating.SAFE, ExternalRating.strongestTier(listOf(ExternalRating.SAFE)))
        assertEquals(ContentRating.MATURE, ExternalRating.strongestTier(listOf(ExternalRating.SAFE, ExternalRating.SUGGESTIVE)))
        assertEquals(ContentRating.MATURE, ExternalRating.strongestTier(listOf(ExternalRating.SUGGESTIVE, ExternalRating.EROTICA)))
        assertEquals(ContentRating.EXPLICIT, ExternalRating.strongestTier(listOf(ExternalRating.SAFE, ExternalRating.PORNOGRAPHIC)))
        assertEquals(ContentRating.EXPLICIT, ExternalRating.strongestTier(listOf(ExternalRating.SUGGESTIVE, ExternalRating.PORNOGRAPHIC)))
        assertEquals(ContentRating.EXPLICIT, ExternalRating.strongestTier(listOf(ExternalRating.EROTICA, ExternalRating.PORNOGRAPHIC)))
        assertEquals(ContentRating.EXPLICIT, ExternalRating.strongestTier(ExternalRating.entries))
    }

    // --- MangaDex: `contentRating` string → evidence → tier ------------------

    @Test fun `mangaDex content rating values map through the evidence model`() {
        assertEquals(ContentRating.SAFE, ExternalRating.fromApiValue("safe")!!.tier)
        assertEquals(ContentRating.MATURE, ExternalRating.fromApiValue("suggestive")!!.tier)
        assertEquals(ContentRating.MATURE, ExternalRating.fromApiValue("erotica")!!.tier)
        assertEquals(ContentRating.EXPLICIT, ExternalRating.fromApiValue("pornographic")!!.tier)
        assertNull(ExternalRating.fromApiValue(null))
        assertNull(ExternalRating.fromApiValue(""))
        assertNull(ExternalRating.fromApiValue("whatever"))
    }

    @Test fun `mangaDex pornographic signal is explicit end to end`() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown series", externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
        )))
    }

    // --- AniList: `isAdult` flag and mature-theme tags ------------------------

    @Test fun `aniList adult flag is pornographic evidence`() {
        assertEquals(ExternalRating.PORNOGRAPHIC, aniListContentRating(isAdult = true, hasMatureThemeTag = false))
        assertEquals(ExternalRating.PORNOGRAPHIC, aniListContentRating(isAdult = true, hasMatureThemeTag = true))
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown series", externalRatings = listOf(aniListContentRating(isAdult = true, hasMatureThemeTag = false)!!),
        )))
    }

    @Test fun `aniList mature theme tags are mature evidence and absent signals are absent`() {
        assertEquals(ExternalRating.SUGGESTIVE, aniListContentRating(isAdult = false, hasMatureThemeTag = true))
        assertNull(aniListContentRating(isAdult = false, hasMatureThemeTag = false))
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown series", externalRatings = listOf(aniListContentRating(isAdult = false, hasMatureThemeTag = true)!!),
        )))
        assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput(
            "Unknown series", externalRatings = listOfNotNull(aniListContentRating(isAdult = false, hasMatureThemeTag = false)),
        )))
    }

    // --- Kitsu: `ageRating` + `nsfw` companion flag ---------------------------

    @Test fun `kitsu age ratings map to tiers`() {
        assertEquals(ExternalRating.SAFE, kitsuContentRating("G"))
        assertEquals(ExternalRating.SAFE, kitsuContentRating("PG"))
        assertEquals(ExternalRating.SUGGESTIVE, kitsuContentRating("R"))
        assertEquals(ExternalRating.PORNOGRAPHIC, kitsuContentRating("R18"))
        assertNull(kitsuContentRating(null))
        assertNull(kitsuContentRating("unknown"))
    }

    @Test fun `kitsu r17 is mature and only explicit when the nsfw flag accompanies it`() {
        assertEquals(ContentRating.MATURE, kitsuContentRating("R")!!.tier)
        assertEquals(ContentRating.MATURE, kitsuContentRating("R", nsfw = false)!!.tier)
        assertEquals(ExternalRating.PORNOGRAPHIC, kitsuContentRating("R", nsfw = true))
        assertEquals(ContentRating.EXPLICIT, kitsuContentRating("R", nsfw = true)!!.tier)
        assertEquals(ContentRating.EXPLICIT, kitsuContentRating("R18")!!.tier)
    }

    @Test fun `kitsu r17 stays mature end to end`() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown series", externalRatings = listOf(kitsuContentRating("R")!!),
        )))
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown series", externalRatings = listOf(kitsuContentRating("R", nsfw = true)!!),
        )))
    }

    // --- MangaUpdates (Baka): Mature genre labels -----------------------------

    @Test fun `baka mature genre label is mature`() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown series", genres = listOf("Mature"),
        )))
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown series", genres = listOf("Adult"),
        )))
    }

    // --- Cross-provider conflicts ---------------------------------------------

    @Test fun `conflicting providers resolve to the cautious upper tier`() {
        // MangaDex says safe, AniList says adult: EXPLICIT wins.
        assertEquals(ContentRating.EXPLICIT, ExternalRating.strongestTier(listOf(
            ExternalRating.fromApiValue("safe")!!,
            aniListContentRating(isAdult = true, hasMatureThemeTag = false)!!,
        )))
        // Kitsu says R17, MangaDex says pornographic: EXPLICIT wins.
        assertEquals(ContentRating.EXPLICIT, ExternalRating.strongestTier(listOf(
            kitsuContentRating("R")!!,
            ExternalRating.fromApiValue("pornographic")!!,
        )))
        // Baka has no rating signal, MangaDex says suggestive: MATURE wins.
        assertEquals(ContentRating.MATURE, ExternalRating.strongestTier(listOf(
            ExternalRating.fromApiValue("suggestive")!!,
        )))
    }

    @Test fun `classifier honours the cautious upper tier across providers`() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown series",
            externalRatings = listOf(
                ExternalRating.fromApiValue("safe")!!,
                kitsuContentRating("R")!!,
                aniListContentRating(isAdult = true, hasMatureThemeTag = false)!!,
            ),
        )))
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown series",
            externalRatings = listOf(
                ExternalRating.fromApiValue("safe")!!,
                kitsuContentRating("R")!!,
            ),
        )))
    }

    @Test fun `serialized fiction context still corrects provider pornographic ratings`() {
        // The tier mapping is the entry point, not the whole verdict: publication
        // context keeps serialized 18+ stories on the public 18+ rails.
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown series", genres = listOf("Isekai", "Fantasy"),
            externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
        )))
    }
}
