package com.yomira.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography()

val YomiraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = FontFamily.SansSerif),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = FontFamily.SansSerif),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
)
