package com.yomira.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.yomira.core.model.Manga
import com.yomira.core.model.ContentRating
import com.yomira.domain.extension.FavoriteSeries
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repositoryPrimaryAddsOnlyAfterConfirmationAndCancelKeepsStep() {
        var additions = 0
        var skips = 0
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { _, _, _, _, _ -> skips++ },
                onCompleteWithRepository = { _, _, _, _, _ -> additions++ })
        } }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Add repository").performClick()
        compose.onNodeWithText("Add the official repository?").assertExists()
        assertEquals(0, additions)
        assertEquals(0, skips)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Content sources").assertExists()
        compose.onNodeWithText("Add repository").performClick()
        compose.onNodeWithTag("onboarding-repository-confirm").performClick()
        assertEquals(1, additions)
        assertEquals(0, skips)
    }

    @Test fun fourStepsCollectLanguageAgeBlacklistAndTaste() {
        var result: List<Any>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, excluded, _, favorites -> result = listOf(language, adult, excluded, favorites) },
                catalog = listOf(Manga(id = "one", title = "Beloved", sourceId = "test", contentRating = ContentRating.SAFE)), showRepositoryStep = false)
        } }
        compose.onNodeWithText("Select your reading language").assertExists()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("🔞 I am 18 or older").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🚫 Yaoi / BL").performClick()
        compose.onNodeWithText("🚫 Futanari").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Start Exploring").assertIsNotEnabled()
        compose.onNodeWithText("Search series").performScrollTo().performTextInput("Beloved")
        compose.onNodeWithTag("favorite-series-one").performScrollTo().performClick()
        compose.onNodeWithText("Start Exploring").performClick()
        assertEquals(listOf("en", true, setOf("yaoi", "futanari"), listOf(FavoriteSeries("one", "Beloved"))), result)
    }

    @Test fun minorSelectionCompletesInSafeMode() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, _, _, _ -> result = language to adult }, showRepositoryStep = false)
        } }
        compose.onNodeWithText("Türkçe").assertIsEnabled()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals("en" to false, result)
    }

    @Test fun backNavigationPreservesLanguageSelection() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, _, _, _ -> result = language to adult }, showRepositoryStep = false)
        } }
        compose.onNodeWithText("Both (Türkçe + English)").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals("both" to false, result)
    }

    @Test fun savingDisablesNavigationAndDuplicateSubmission() {
        compose.setContent { MaterialTheme { OnboardingScreen(true, null, { _, _, _, _, _ -> error("Unexpected submission") }) } }
        compose.onNodeWithText("Saving setup…").assertIsNotEnabled()
        compose.onNodeWithText("English").assertIsNotEnabled()
        compose.onNodeWithText("Both (Türkçe + English)").assertIsNotEnabled()
    }

    @Test fun languageSelectionNotifiesHostImmediately() {
        val selected = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { _, _, _, _, _ -> }, onLanguageSelected = { selected += it })
        } }
        compose.onNodeWithText("Türkçe").performClick()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Both (Türkçe + English)").performClick()
        assertEquals(listOf("tr", "en", "both"), selected)
    }

    @Test fun fiveSelectionLimitAllowsRemovalAndReplacement() {
        val catalog = (1..6).map { Manga(id = "$it", title = "Series $it", sourceId = "test", contentRating = ContentRating.SAFE) }
        val initial = catalog.take(5).map { FavoriteSeries(it.id, it.title) }
        var result: List<FavoriteSeries>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { _, _, _, _, favorites -> result = favorites },
                catalog = catalog, initialFavoriteSeries = initial, showRepositoryStep = false)
        } }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Search series").performScrollTo().performTextInput("Series 6")
        compose.onNodeWithTag("favorite-series-6").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Remove: Series 1 ×").performScrollTo().performClick()
        compose.onNodeWithTag("favorite-series-6").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Start Exploring").performClick()
        assertEquals(initial.drop(1) + FavoriteSeries("6", "Series 6"), result)
    }

    @Test fun skipSubmitsEmptyFavoritesEvenWithSelectedSeries() {
        var result: List<FavoriteSeries>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { _, _, _, _, favorites -> result = favorites },
                initialFavoriteSeries = listOf(FavoriteSeries("one", "One")), showRepositoryStep = false)
        } }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(emptyList<FavoriteSeries>(), result)
    }

    @Test fun firstRunSkipTasteContinuesToRepositoryAndCompletes() {
        var result: List<FavoriteSeries>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { _, _, _, _, favorites -> result = favorites },
                initialFavoriteSeries = listOf(FavoriteSeries("one", "One")))
        } }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Content sources").assertExists()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(emptyList<FavoriteSeries>(), result)
    }
}
