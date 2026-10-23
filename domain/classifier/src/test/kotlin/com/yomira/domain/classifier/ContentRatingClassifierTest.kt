package com.yomira.domain.classifier

import com.yomira.core.model.ContentRating
import com.yomira.core.model.ExternalRating
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentRatingClassifierTest {
    private val classifier = ContentRatingClassifier()

    @Test fun generalAudienceTitlesStaySafe() {
        listOf("One Piece", "Solo Leveling", "Spider-Man").forEach {
            assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput(it)))
        }
    }

    @Test fun serializedSexualStoriesAreMatureNotPornographic() {
        listOf("I Formed a MILF Party with the Heroes’ Mothers", "Kimi wa Midara na Boku no Joou", "Prison School").forEach {
            assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(it, externalRatings = listOf(ExternalRating.EROTICA))))
        }
    }

    @Test fun hentaiAndPornhwaOnlyGoToVault() {
        listOf("Hentai", "Pornhwa", "Pornographic").forEach {
            assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown", tags = listOf(it))))
        }
    }

    @Test fun explicitEvidenceCannotBeOverriddenByTitleOrOfficialPublication() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Prison School", externalRatings = listOf(ExternalRating.PORNOGRAPHIC), isOfficialPublication = true,
        )))
    }

    @Test fun conflictingProvidersUseStrongestEvidence() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown",
            externalRatings = listOf(ExternalRating.SAFE, ExternalRating.PORNOGRAPHIC))))
    }

    @Test fun ecchiDoesNotRequireAnAllowlistedTitle() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput("New story", tags = listOf("Ecchi"))))
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown", externalRatings = listOf(ExternalRating.EROTICA))))
    }

    @Test fun titleKeywordsAndDoujinshiAloneAreNotPornographicEvidence() {
        assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput("Hentai Detective", publicationType = "Doujinshi")))
    }

    @Test fun serializedEroticManhwaIsNotDowngradedByDramaOrPublisher() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown adult manhwa", genres = listOf("Drama"), publicationType = "Manhwa",
            isOfficialPublication = true, externalRatings = listOf(ExternalRating.EROTICA),
        )))
    }

    @Test fun associatedTitlesSupportTranslatedNames() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput("監獄学園", altTitles = listOf("Kangoku Gakuen"))))
    }
}
