package com.yomira.feature.home

internal fun OnboardingCopy.primaryActionLabel(step: Int, totalSteps: Int): String =
    if (step == 4) addRepository else if (step == totalSteps - 1) connectAndStart else continueAction
