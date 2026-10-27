package com.yomira.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun englishCompletesInOneStepWithoutRepositoryOrContentPrompt() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, repository -> result = language to repository })
        } }
        compose.onNodeWithText("Hangi dillerdeki serileri okumak istersiniz?").assertExists()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Devam et").assertDoesNotExist()
        compose.onNodeWithText("Keiyoushi").assertDoesNotExist()
        compose.onNodeWithContentDescription("Yetişkin içerik izni").assertDoesNotExist()
        compose.onNodeWithText("Başla ve Keşfet").performClick()
        assertEquals("en" to true, result)
    }

    @Test fun defaultsRemainSafeAndCompletionAlwaysKeepsBundledRepositories() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, repository -> result = language to repository })
        } }
        compose.onNodeWithText("Türkçe").assertIsEnabled()
        compose.onNodeWithText("Başla ve Keşfet").performClick()
        assertEquals("tr" to true, result)
    }

    @Test fun bothLanguagesCompletesWithoutAnAdditionalSetupQuestion() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, repository -> result = language to repository })
        } }
        compose.onNodeWithText("Her İkisi (Both)").performClick()
        compose.onNodeWithText("Başla ve Keşfet").performClick()
        assertEquals("both" to true, result)
    }

    @Test fun savingDisablesNavigationAndDuplicateSubmission() {
        compose.setContent { MaterialTheme { OnboardingScreen(true, null, { _, _ -> error("Unexpected submission") }) } }
        compose.onNodeWithText("Kurulum kaydediliyor…").assertIsNotEnabled()
        compose.onNodeWithText("English").assertIsNotEnabled()
        compose.onNodeWithText("Her İkisi (Both)").assertIsNotEnabled()
    }
}
