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

    @Test fun knownMatureSerialsCorrectMisleadingProviderRatings() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Prison School", externalRatings = listOf(ExternalRating.PORNOGRAPHIC), isOfficialPublication = true,
        )))
    }

    @Test fun conflictingProvidersUseStrongestEvidence() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown",
            externalRatings = listOf(ExternalRating.SAFE, ExternalRating.PORNOGRAPHIC))))
    }

    @Test fun ecchiDoesNotRequireAnAllowlistedTitle() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput("New story", tags = listOf("Ecchi"))))
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput("Unknown", externalRatings = listOf(ExternalRating.EROTICA))))
    }

    @Test fun titleKeywordsAndDoujinshiAloneAreNotPornographicEvidence() {
        assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput("Hentai Detective", publicationType = "Doujinshi")))
    }

    @Test fun serializedEroticManhwaDoesNotBecomePornWithoutExplicitEvidence() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown adult manhwa", genres = listOf("Drama"), publicationType = "Manhwa",
            isOfficialPublication = true, externalRatings = listOf(ExternalRating.EROTICA),
        )))
    }

    @Test fun associatedTitlesSupportTranslatedNames() {
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput("監獄学園", altTitles = listOf("Kangoku Gakuen"))))
    }

    @Test fun milfPartyVariantsStayPublicEvenWithIncorrectHentaiMetadata() {
        listOf(
            "I Formed a MILF Party with the Heroes' Mothers",
            " I FORMED A MILF PARTY WITH THE HEROES’ MOTHERS! ",
            "I Formed a MILF Party with the Heroes Mothers",
            "Yuusha no Haha",
            "Yuusha no Haha Oya to Party wo Kumimashita!",
            "Yuusha no Hahaoya-tachi to Party wo Kumimashita!",
            "I Formed a MILF Party with the Heroes' Mothers! — Volume 1",
            "I Formed a MILF Party with the Hero's Mothers",
            "Yuusha ni Zenbu Ubawareta Ore wa Yuusha no Hahaoya to Party wo Kumimashita!",
            "Betrayed by the Hero, I Formed a MILF Party with His Mom!",
        ).forEach { title ->
            assertEquals(title, ContentRating.MATURE, classifier.classify(ClassificationInput(
                title, tags = listOf("Hentai"), externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
            )))
        }
    }

    @Test fun ecchiAndEroticaAreMatureRegardlessOfPublicationFormatOrOfficialStatus() {
        listOf(null, "Manga", "Comic", "Manhwa", "Manhua", "Webtoon", "Doujinshi").forEach { type ->
            listOf(null, true, false).forEach { official ->
                assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
                    "New serialized story", genres = listOf("Romance, Ecchi; Erotica"),
                    publicationType = type, isOfficialPublication = official,
                )))
            }
        }
    }

    @Test fun adultWebtoonAndSmutAreNotHardcoreEvidence() {
        listOf("Adult Webtoon", "Adult Webtoons", "Smut", "Mature", "Fanservice", "Suggestive Romance",
            "3D Comic", "3D Comics", "18+", "R-18", "Western Erotica", "Erotic Comics").forEach { tag ->
            assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput("Unknown", tags = listOf(tag))))
        }
    }

    @Test fun pornographicPlatformsAndComicTagsAreExplicit() {
        listOf("AllPornComic", "ALLPORNCOMICS", "All Porn Comic", "3D Porn",
            "18+ Pornographic Comics", "Explicit Doujinshi", "Explicit Doujin",
            "Pornographic Webtoon", "Hardcore", "Romance | allporncomic; Drama").forEach { tag ->
            assertEquals(tag, ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown", tags = listOf(tag))))
        }
    }

    @Test fun adultWebtoonWithPornhwaEvidenceIsExplicit() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown", tags = listOf("Adult Webtoon", "Ecchi", "Pornhwa"),
        )))
    }

    @Test fun platformNamesInTitlesAndLookalikeTagsAreNotEvidence() {
        assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput(
            "AllPornComic Detective", tags = listOf("Nonhentai", "NotAllPornComic", "NotPornhwa"),
        )))
    }

    @Test fun knownEcchiTitlesStayMatureDespiteMisleadingPlatformTags() {
        listOf("Prison School", "Kangoku Gakuen").forEach { title ->
            assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
                title, tags = listOf("AllPornComic", "Hentai", "Hardcore"),
            )))
        }
    }

    @Test fun explicitTagsAreRecognizedInsideDelimitedProviderLabels() {
        listOf("Romance, Drama, Hentai", "Ecchi | Explicit Sexual Content", "Uncensored Genitalia").forEach { tag ->
            assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown", tags = listOf(tag))))
        }
    }

    @Test fun officialPublicationCannotHideActualPornographicEvidence() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown", genres = listOf("Drama", "Ecchi"), publicationType = "Webtoon",
            isOfficialPublication = true, externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
        )))
    }
}
