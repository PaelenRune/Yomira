package com.yomira.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun englishReachesRepositoryCompletionInTwoStepsWithoutContentPrompt() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, repository -> result = language to repository })
        } }
        compose.onNodeWithText("1 / 2").assertExists()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithContentDescription("Yetişkin içerik izni").assertDoesNotExist()
        assertNull(result)
        compose.onNodeWithText("Depoları Bağla ve Başla").performClick()
        assertEquals("en" to true, result)
    }

    @Test fun defaultsRemainSafeAndUserCanSkipRepository() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, repository -> result = language to repository })
        } }
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Geri").performClick()
        compose.onNodeWithText("Türkçe").assertIsEnabled()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Hadi Başlayalım · Deposuz devam et").performClick()
        assertEquals("tr" to false, result)
    }

    @Test fun savingDisablesNavigationAndDuplicateSubmission() {
        compose.setContent { MaterialTheme { OnboardingScreen(true, null, { _, _ -> error("Unexpected submission") }) } }
        compose.onNodeWithText("Kurulum kaydediliyor…").assertIsNotEnabled()
        compose.onNodeWithText("English").assertIsNotEnabled()
    }
}
