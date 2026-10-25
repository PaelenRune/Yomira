package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Desktop palette. It mirrors the Android OLED brand tokens (`core:ui`) but is
 * declared locally because that module is an Android library and must not be
 * pulled into the desktop classpath.
 */
object YomiraColors {
    val Oled = Color(0xFF050409)
    val OledRaised = Color(0xFF0B0913)
    val Surface = Color(0xFF12101F)
    val SurfaceRaised = Color(0xFF1B182B)
    val Outline = Color(0xFF2B263D)
    val Purple = Color(0xFF9D4EDD)
    val Fuchsia = Color(0xFFFF007F)
    val Magenta = Color(0xFFE04BFF)
    val TextPrimary = Color(0xFFF7F5FF)
    val TextMuted = Color(0xFF8E8D9A)
    val Gold = Color(0xFFFFB703)
    val Success = Color(0xFF57E3A1)
    val Canvas get() = Oled
    val Panel get() = Surface
    val Hairline get() = Outline
    val Ink get() = TextPrimary
    val InkMuted get() = TextMuted
    val Violet get() = Purple
    val Cyan = Color(0xFF00E5FF)
    val Amber get() = Gold
}

val Canvas get() = YomiraColors.Oled
val Panel get() = YomiraColors.Surface
val Hairline get() = YomiraColors.Outline
val Ink get() = YomiraColors.TextPrimary
val InkMuted get() = YomiraColors.TextMuted
val Violet get() = YomiraColors.Purple
val Cyan = Color(0xFF00E5FF)
val Amber get() = YomiraColors.Gold

private val DesktopColorScheme = darkColorScheme(
    primary = YomiraColors.Purple,
    onPrimary = YomiraColors.Oled,
    primaryContainer = Color(0xFF321448),
    onPrimaryContainer = YomiraColors.TextPrimary,
    secondary = YomiraColors.Fuchsia,
    onSecondary = YomiraColors.Oled,
    secondaryContainer = Color(0xFF4D0027),
    onSecondaryContainer = YomiraColors.TextPrimary,
    tertiary = YomiraColors.Gold,
    background = YomiraColors.Oled,
    onBackground = YomiraColors.TextPrimary,
    surface = YomiraColors.Surface,
    onSurface = YomiraColors.TextPrimary,
    surfaceVariant = YomiraColors.SurfaceRaised,
    onSurfaceVariant = YomiraColors.TextMuted,
    outline = YomiraColors.Outline,
    error = Color(0xFFFF8A93),
    onError = Color(0xFF3A0006),
)

private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif
private val Numerals = FontFamily.Monospace

private val DesktopTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 1.4.sp,
    ),
)

/** Numeric HUD style: tabular, never shifts while paging. */
val NumericStyle = TextStyle(
    fontFamily = Numerals,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.6.sp,
)

@Composable
fun YomiraDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DesktopColorScheme,
        typography = DesktopTypography,
        content = content,
    )
}

/**
 * Near-black OLED canvas with two restrained brand glows. One draw pass: the
 * desktop grid is already expensive at 4K, so no blur shaders.
 */
@Composable
fun OledBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(
                Brush.verticalGradient(
                    0f to YomiraColors.Oled,
                    0.45f to Color(0xFF08060F),
                    1f to Color(0xFF040309),
                ),
            )
            .drawBehind {
                // Brand glows scale with the window, so an ultrawide 4K canvas keeps
                // the same composition instead of pinning them to 1080p pixels.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(YomiraColors.Purple.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.86f, -size.height * 0.08f),
                        radius = size.width * 0.52f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(YomiraColors.Fuchsia.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.04f, size.height * 1.02f),
                        radius = size.width * 0.42f,
                    ),
                )
            },
    ) {
        content()
    }
}

/** Standard gutter of the desktop shell; scales with the window, not the display DPI. */
val ShellPadding = 36.dp
