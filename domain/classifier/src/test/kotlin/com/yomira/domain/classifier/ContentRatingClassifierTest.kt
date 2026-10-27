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

    @Test fun directPornographicTagsGoToVault() {
        listOf("Pornhwa", "Pornographic").forEach {
            assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown", tags = listOf(it))))
        }
    }

    @Test fun officialSerializedFictionCanCorrectProviderPornographicRatings() {
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

    @Test fun primaryAndTranslatedTitlesAreNotClassificationEvidence() {
        assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput("監獄学園", altTitles = listOf("Kangoku Gakuen"))))
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
                title, genres = listOf("Isekai", "Fantasy"), tags = listOf("Hentai"),
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

    @Test fun familiarTitlesCannotHideHardcoreEvidence() {
        listOf("Prison School", "Kangoku Gakuen").forEach { title ->
            assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
                title, tags = listOf("AllPornComic", "Hentai", "Hardcore"),
            )))
        }
    }

    @Test fun explicitTagsAreRecognizedInsideDelimitedProviderLabels() {
        listOf("Romance, Drama, Pornhwa", "Ecchi | Explicit Sexual Content", "Uncensored Genitalia").forEach { tag ->
            assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput("Unknown", tags = listOf(tag))))
        }
    }

    @Test fun officialPublicationCannotHideActualPornographicEvidence() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown", genres = listOf("Drama", "Ecchi"), publicationType = "Webtoon",
            isOfficialPublication = true, tags = listOf("Hardcore"), externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
        )))
    }

    @Test fun commercialSerialsUseMetadataRatherThanTheirNames() {
        listOf(
            "Tsugumomo" to listOf("Action", "Supernatural", "Seinen"),
            "Reincarnated Carrier's Strategy" to listOf("Isekai", "Adventure", "Fantasy"),
            "Yabadere Gyaru" to listOf("Gyaru", "School Life", "Romance", "Comedy"),
            "To Love-Ru" to listOf("Shounen", "Romance", "Comedy"),
            "Prison School" to listOf("School Life", "Comedy", "Seinen"),
            "Previously unseen ecchi isekai" to listOf("Isekai", "Fantasy"),
        ).forEach { (title, genres) ->
            listOf(title, "Unrecognized translated title").forEach { name ->
                assertEquals(title, ContentRating.MATURE, classifier.classify(ClassificationInput(
                    name, genres = genres, tags = listOf("Ecchi", "Fanservice", "Hentai"),
                )))
            }
        }
    }

    @Test fun verifiedNonPornographicRatingsAreNotOverriddenByScraperHentaiTags() {
        listOf(ExternalRating.SAFE, ExternalRating.SUGGESTIVE, ExternalRating.EROTICA).forEach { rating ->
            assertEquals(rating.name, ContentRating.MATURE, classifier.classify(ClassificationInput(
                "Unknown", tags = listOf("Japanese Hentai Manga"), externalRatings = listOf(rating),
            )))
        }
        assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput(
            "Unknown", externalRatings = listOf(ExternalRating.SAFE),
        )))
    }

    @Test fun standaloneHentaiIsExplicitWithoutContradictoryEvidence() {
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            "Unknown", tags = listOf("Hentai"),
        )))
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Unknown", tags = listOf("Hentai"), isOfficialPublication = true,
        )))
    }

    @Test fun fictionContextCorrectsProviderRatingsWithoutRelyingOnTitleKeywords() {
        listOf("Action", "Isekai", "Adventure", "Fantasy", "Comedy", "School Life",
            "Gyaru", "Supernatural", "Shounen", "Seinen").forEach { genre ->
            assertEquals(genre, ContentRating.MATURE, classifier.classify(ClassificationInput(
                "Unrecognized story", genres = listOf(genre), tags = listOf("Hentai"),
                externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
            )))
        }
    }

    @Test fun knownSerializedTitleCorrectionSurvivesMissingMetadataAndTranslations() {
        val title = "I Formed a MILF Party with the Heroes' Mothers"
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(title)))
        assertEquals(ContentRating.MATURE, classifier.classify(ClassificationInput(
            "Localized title", altTitles = listOf(title), externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
        )))
        assertEquals(ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
            title, tags = listOf("Genitalia"), externalRatings = listOf(ExternalRating.PORNOGRAPHIC),
        )))
    }

    @Test fun hentaiDoujinAndHardcoreRemainExplicitEvenWithFictionContext() {
        listOf("Doujinshi", "Explicit Doujin", "Hardcore", "Genitalia", "Uncensored Sex").forEach { tag ->
            assertEquals(tag, ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
                "Unknown", genres = listOf("Action", "Hentai"), tags = listOf(tag),
                isOfficialPublication = true,
            )))
        }
    }

    @Test fun directEvidenceOverridesEverySoftRatingAndMainstreamGenre() {
        listOf(ExternalRating.SAFE, ExternalRating.SUGGESTIVE, ExternalRating.EROTICA).forEach { rating ->
            listOf("Pornhwa", "Explicit Doujinshi", "Hardcore", "Explicit Sex", "Genitalia",
                "Uncensored Genitalia", "Uncensored Sex", "Explicit Sexual Content").forEach { tag ->
                assertEquals("$rating: $tag", ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
                    "Tsugumomo", genres = listOf("Romance", "Isekai", "Comedy"),
                    tags = listOf("Ecchi", tag), externalRatings = listOf(rating),
                )))
            }
            listOf("allporncomic", "manhwa18", "hentai2read", "nhentai", "en.allporncomic").forEach { source ->
                assertEquals("$rating: $source", ContentRating.EXPLICIT, classifier.classify(ClassificationInput(
                    "Prison School", genres = listOf("Comedy"), tags = listOf("Ecchi"),
                    sourceId = source, externalRatings = listOf(rating),
                )))
            }
        }
    }

    @Test fun mainstreamGenresWithoutSexualEvidenceStaySafe() {
        assertEquals(ContentRating.SAFE, classifier.classify(ClassificationInput(
            "New adventure", genres = listOf("Action", "Adventure", "Fantasy", "Shounen"),
        )))
    }
}
