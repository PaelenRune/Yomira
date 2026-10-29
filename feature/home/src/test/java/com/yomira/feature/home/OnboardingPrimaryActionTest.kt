package com.yomira.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPrimaryActionTest {
    @Test fun repositoryPrimarySaysAddNotSkipInEitherLanguage() {
        for (copy in listOf(OnboardingCopy(), OnboardingCopy(addRepository = "Depo ekle", skip = "Atla"))) {
            assertEquals(copy.addRepository, copy.primaryActionLabel(4, 5))
        }
    }
    @Test fun surveyWithoutRepositoryKeepsStartAction() {
        val copy = OnboardingCopy()
        assertEquals(copy.connectAndStart, copy.primaryActionLabel(3, 4))
    }
    @Test fun earlierStepsKeepContinueIncludingTasteBeforeRepository() {
        val copy = OnboardingCopy()
        for (step in 0..3) assertEquals(copy.continueAction, copy.primaryActionLabel(step, 5))
    }
}
