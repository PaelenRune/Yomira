package com.yomira.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fourStepsCollectLanguageAgeBlacklistAndTaste() {
        var result: List<Any>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, excluded, favorites -> result = listOf(language, adult, excluded, favorites) })
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
        compose.onNodeWithText("Isekai").performClick()
        compose.onNodeWithText("Start Exploring").performClick()
        assertEquals(listOf("en", true, setOf("yaoi", "futanari"), setOf("isekai")), result)
    }

    @Test fun minorSelectionCompletesInSafeMode() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, _, _ -> result = language to adult })
        } }
        compose.onNodeWithText("Türkçe").assertIsEnabled()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Start Exploring").performClick()
        assertEquals("en" to false, result)
    }

    @Test fun backNavigationPreservesLanguageSelection() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, _, _ -> result = language to adult })
        } }
        compose.onNodeWithText("Both (Türkçe + English)").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("🛡️ I am under 18").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Start Exploring").performClick()
        assertEquals("both" to false, result)
    }

    @Test fun savingDisablesNavigationAndDuplicateSubmission() {
        compose.setContent { MaterialTheme { OnboardingScreen(true, null, { _, _, _, _ -> error("Unexpected submission") }) } }
        compose.onNodeWithText("Saving setup…").assertIsNotEnabled()
        compose.onNodeWithText("English").assertIsNotEnabled()
        compose.onNodeWithText("Both (Türkçe + English)").assertIsNotEnabled()
    }

    @Test fun languageSelectionNotifiesHostImmediately() {
        val selected = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { _, _, _, _ -> }, onLanguageSelected = { selected += it })
        } }
        compose.onNodeWithText("Türkçe").performClick()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Both (Türkçe + English)").performClick()
        assertEquals(listOf("tr", "en", "both"), selected)
    }
}
