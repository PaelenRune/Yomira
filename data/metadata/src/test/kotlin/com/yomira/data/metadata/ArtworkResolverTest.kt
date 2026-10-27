package com.yomira.data.metadata

import com.yomira.core.model.ExternalRating
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.RemoteMetadata
import org.junit.Assert.*
import org.junit.Test

class ArtworkResolverTest {
    @Test fun westernProvidersBeatEnglishTitleHintsAndJapaneseOriginals() {
        for (source in listOf(MetadataSources.COMICVINE, MetadataSources.OPENLIBRARY)) {
            val original = record(MetadataSources.ANILIST, "https://img.test/original.jpg")
                .copy(englishTitle = "Series")
            val japanese = record(MetadataSources.KITSU, "https://img.test/ja.jpg")
                .copy(coverLocale = "ja", englishTitle = "Series")
            val western = record(source, "https://img.test/western.jpg")
            assertEquals(western.coverUrl, ArtworkResolver.cover(listOf(original, japanese, western)))
        }
    }

    @Test fun explicitEnglishCoverFromAnyProviderBeatsAniListOriginal() {
        val original = record(MetadataSources.ANILIST, "https://img.test/original.jpg")
            .copy(englishTitle = "Series")
        for (source in listOf(MetadataSources.KITSU, MetadataSources.MANGADEX,
            MetadataSources.COMICVINE, MetadataSources.BAKA_UPDATES, MetadataSources.OPENLIBRARY)) {
            val english = record(source, "https://img.test/en.jpg").copy(coverLocale = "en")
            assertEquals(english.coverUrl, ArtworkResolver.cover(listOf(original, english)))
        }
    }

    @Test fun nonEnglishLocalesCannotBePromotedByProviderOrEnglishTitles() {
        for (source in listOf(MetadataSources.ANILIST, MetadataSources.COMICVINE, MetadataSources.OPENLIBRARY)) {
            for (locale in listOf("ja", "ko", "zh", "fr")) {
                val localized = record(source, "https://img.test/localized.jpg")
                    .copy(coverLocale = locale, englishTitle = "Series", altTitles = mapOf("en" to listOf("Series")))
                val unknown = record(MetadataSources.BAKA_UPDATES, "https://img.test/unknown.jpg")
                assertEquals(unknown.coverUrl, ArtworkResolver.cover(listOf(localized, unknown)))
            }
        }
    }

    @Test fun sourcePriorityBreaksTiesWithinEnglishTierOnly() {
        val kitsu = record(MetadataSources.KITSU, "https://img.test/kitsu-en.jpg").copy(coverLocale = "en")
        val mangaDex = record(MetadataSources.MANGADEX, "https://img.test/md-en.jpg").copy(coverLocale = "en")
        assertEquals(kitsu.coverUrl, ArtworkResolver.cover(listOf(mangaDex, kitsu)))
    }

    @Test fun explicitEnglishCoverWinsOverProviderQualityAndEnglishTitleHints() {
        val japanese = record(MetadataSources.ANILIST, "https://img.test/ja.jpg")
            .copy(coverLocale = "ja", englishTitle = "Series")
        val unknown = record(MetadataSources.KITSU, "https://img.test/unknown.jpg")
            .copy(englishTitle = "Series")
        val english = record(MetadataSources.MANGADEX, "https://img.test/en.jpg")
            .copy(coverLocale = "EN")
        assertEquals(english.coverUrl, ArtworkResolver.cover(listOf(japanese, unknown, english)))
    }

    @Test fun englishTitleHintWinsWhenCoverLocalesAreUnknown() {
        val japanese = record(MetadataSources.ANILIST, "https://img.test/original.jpg")
        val english = record(MetadataSources.KITSU, "https://img.test/english.jpg")
            .copy(altTitles = mapOf("en" to listOf("Series")))
        assertEquals(english.coverUrl, ArtworkResolver.cover(listOf(japanese, english)))
    }

    @Test fun invalidEnglishCoverFallsBackToUsableJapaneseCover() {
        val japanese = record(MetadataSources.MANGADEX, "https://img.test/ja.jpg")
            .copy(coverLocale = "ja")
        val english = record(MetadataSources.KITSU, "http://img.test/en.jpg")
            .copy(coverLocale = "en")
        assertEquals(japanese.coverUrl, ArtworkResolver.cover(listOf(english, japanese)))
    }

    @Test fun coverLocaleDoesNotChangeLandscapePriority() {
        val japanese = record(MetadataSources.ANILIST, banner = "https://img.test/banner.jpg")
            .copy(coverLocale = "ja")
        val english = record(MetadataSources.MANGADEX, banner = "https://img.test/other.jpg")
            .copy(coverLocale = "en")
        assertEquals(japanese.bannerUrl, ArtworkResolver.backdrop(listOf(english, japanese)))
    }

    private fun record(source: String, cover: String? = null, banner: String? = null) =
        RemoteMetadata(source, "1", "Series", coverUrl = cover, bannerUrl = banner)

    @Test fun highResolutionArtworkDoesNotFollowIdentityPriority() {
        val records = listOf(
            record(MetadataSources.BAKA_UPDATES, "https://img.test/thumbnail.jpg"),
            record(MetadataSources.ANILIST, "https://img.test/extra-large.jpg", "https://img.test/banner.jpg"),
        )
        assertEquals("https://img.test/extra-large.jpg", ArtworkResolver.cover(records))
        assertEquals("https://img.test/banner.jpg", ArtworkResolver.backdrop(records))
    }

    @Test fun kitsuFillsMissingAniListLandscape() {
        val records = listOf(
            record(MetadataSources.ANILIST),
            record(MetadataSources.KITSU, banner = "https://img.test/kitsu-original.jpg"),
        )
        assertEquals("https://img.test/kitsu-original.jpg", ArtworkResolver.backdrop(records))
    }

    @Test fun westernCoverIsNotMisrepresentedAsLandscape() {
        val records = listOf(record(MetadataSources.COMICVINE, "https://img.test/original.jpg"))
        assertEquals("https://img.test/original.jpg", ArtworkResolver.cover(records))
        assertNull(ArtworkResolver.backdrop(records))
    }

    @Test fun explicitArtworkIsRetainedAndMissingBannerUsesUiFallback() {
        val record = record(MetadataSources.MANGADEX, "https://img.test/original.jpg")
            .copy(contentRating = ExternalRating.PORNOGRAPHIC)
        assertEquals(record.coverUrl, ArtworkResolver.cover(listOf(record)))
        assertNull(ArtworkResolver.backdrop(listOf(record)))
    }

    @Test fun invalidOrInsecureArtworkDoesNotHideUsableFallback() {
        for (bad in listOf("", "http://img.test/a.jpg", "file:///cover.jpg", "https://user:pass@img.test/a", "https://[bad")) {
            val records = listOf(
                record(MetadataSources.ANILIST, bad, bad),
                record(MetadataSources.KITSU, " https://img.test/poster.jpg ", "https://img.test/backdrop.jpg"),
            )
            assertEquals("https://img.test/poster.jpg", ArtworkResolver.cover(records))
            assertEquals("https://img.test/backdrop.jpg", ArtworkResolver.backdrop(records))
        }
    }
}
