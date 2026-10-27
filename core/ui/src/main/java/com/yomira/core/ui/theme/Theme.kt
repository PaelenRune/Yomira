package com.yomira.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val YomiraColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = TextPrimary,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = Color(0xFFF3E8FF),
    inversePrimary = NeonLavender,
    secondary = NeonFuchsia,
    onSecondary = Color(0xFF210009),
    secondaryContainer = PinkContainer,
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = NeonIndigo,
    onTertiary = TextPrimary,
    tertiaryContainer = IndigoContainer,
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = DeepOledBlack,
    onBackground = TextSecondary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextMuted,
    surfaceTint = NeonPurple,
    inverseSurface = TextSecondary,
    inverseOnSurface = DeepOledBlack,
    outline = Outline,
    outlineVariant = HairlineBorder,
    error = Error,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
    surfaceBright = SurfaceHighest,
    surfaceDim = DeepOledBlack,
    surfaceContainerLowest = DeepOledBlack,
    surfaceContainerLow = CardSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceHighest,
)

/**
 * Modern soft-corner scale shared by every card, sheet, chip and dialog.
 * Cards use the 16dp medium shape; capsules use `RoundedCornerShape(percent = 50)`.
 */
val YomiraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Intentional OLED theme: system light mode and dynamic colors do not replace the brand palette. */
@Composable
fun YomiraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YomiraColorScheme,
        typography = YomiraTypography,
        shapes = YomiraShapes,
        content = content,
    )
}
