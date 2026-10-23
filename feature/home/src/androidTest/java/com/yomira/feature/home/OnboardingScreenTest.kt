package com.yomira.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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

    @Test fun englishAndAdultConsentReachRepositoryCompletion() {
        var result: Triple<String, Boolean, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, repository -> result = Triple(language, adult, repository) })
        } }
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithContentDescription("Yetişkin içerik izni").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Devam et").performClick()
        assertNull(result)
        compose.onNodeWithText("Depoyu Bağla ve Başla").performClick()
        assertEquals(Triple("en", true, true), result)
    }

    @Test fun defaultsRemainSafeAndUserCanSkipRepository() {
        var result: Triple<String, Boolean, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, repository -> result = Triple(language, adult, repository) })
        } }
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Geri").performClick()
        compose.onNodeWithText("Türkçe").assertIsEnabled()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithContentDescription("Yetişkin içerik izni").assertIsOff()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Hadi Başlayalım · Deposuz devam et").performClick()
        assertEquals(Triple("tr", false, false), result)
    }

    @Test fun savingDisablesNavigationAndDuplicateSubmission() {
        compose.setContent { MaterialTheme { OnboardingScreen(true, null, { _, _, _ -> error("Unexpected submission") }) } }
        compose.onNodeWithText("Kurulum kaydediliyor…").assertIsNotEnabled()
        compose.onNodeWithText("English").assertIsNotEnabled()
    }
}
