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
        compose.onNodeWithText("Hangi dillerdeki serileri okumak istersiniz?").assertExists()
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Devam et").assertIsNotEnabled()
        compose.onNodeWithText("🔞 18 Yaşından Büyüğüm").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("🚫 Yaoi / BL").performClick()
        compose.onNodeWithText("🚫 Futanari").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Isekai").performClick()
        compose.onNodeWithText("Başla ve Keşfet").performClick()
        assertEquals(listOf("en", true, setOf("yaoi", "futanari"), setOf("isekai")), result)
    }

    @Test fun minorSelectionCompletesInSafeMode() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, _, _ -> result = language to adult })
        } }
        compose.onNodeWithText("Türkçe").assertIsEnabled()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("🛡️ 18 Yaşından Küçüğüm").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Başla ve Keşfet").performClick()
        assertEquals("tr" to false, result)
    }

    @Test fun backNavigationPreservesLanguageSelection() {
        var result: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme {
            OnboardingScreen(false, null, { language, adult, _, _ -> result = language to adult })
        } }
        compose.onNodeWithText("Her İkisi (Both)").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Geri").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("🛡️ 18 Yaşından Küçüğüm").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Devam et").performClick()
        compose.onNodeWithText("Başla ve Keşfet").performClick()
        assertEquals("both" to false, result)
    }

    @Test fun savingDisablesNavigationAndDuplicateSubmission() {
        compose.setContent { MaterialTheme { OnboardingScreen(true, null, { _, _, _, _ -> error("Unexpected submission") }) } }
        compose.onNodeWithText("Kurulum kaydediliyor…").assertIsNotEnabled()
        compose.onNodeWithText("English").assertIsNotEnabled()
        compose.onNodeWithText("Her İkisi (Both)").assertIsNotEnabled()
    }
}
