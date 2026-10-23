package com.yomira.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YomiraColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = DeepOledBlack,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = TextPrimary,
    inversePrimary = PurpleContainer,
    secondary = NeonFuchsia,
    onSecondary = DeepOledBlack,
    secondaryContainer = FuchsiaContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = TextMuted,
    onTertiary = DeepOledBlack,
    tertiaryContainer = SurfaceRaised,
    onTertiaryContainer = TextPrimary,
    background = DeepOledBlack,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextMuted,
    surfaceTint = NeonPurple,
    inverseSurface = TextPrimary,
    inverseOnSurface = DeepOledBlack,
    outline = Outline,
    outlineVariant = SurfaceRaised,
    error = Error,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
    surfaceBright = SurfaceRaised,
    surfaceDim = DeepOledBlack,
    surfaceContainerLowest = DeepOledBlack,
    surfaceContainerLow = CardSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = Color(0xFF252136),
)

/** Intentional OLED theme: system light mode and dynamic colors do not replace the brand palette. */
@Composable
fun YomiraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YomiraColorScheme,
        typography = YomiraTypography,
        content = content,
    )
}
