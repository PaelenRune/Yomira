package com.yomira.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Desktop palette, aligned with the Android OLED brand tokens (`core:ui`) and
 * the v5 "Cstation" web language: one near-black canvas, one plum card tone and
 * a single violet → lavender accent ramp. Hot pink is gone; every legacy call
 * site that used it resolves to [YomiraColors.Lavender] instead.
 */
object YomiraColors {
    /** OLED canvas. Never true black: hairlines and card edges must stay visible. */
    val Oled = Color(0xFF090A0E)

    /** Chrome above the canvas: masthead, rail and the reader HUD base. */
    val OledRaised = Color(0xFF0E0F15)

    /** Cards, posters and floating panels. */
    val Surface = Color(0xFF13141C)

    /** Raised chips, menus and pressed rows. */
    val SurfaceRaised = Color(0xFF1B1C26)

    /** Menus and badge fills one step above raised. */
    val SurfaceHighest = Color(0xFF23242F)

    /** 1dp card border on the plum surface stack. */
    val Outline = Color(0xFF262738)

    /** Soft border for controls that need a quieter edge. */
    val OutlineSoft = Color(0xFF323446)

    /** Brand anchor: primary action, selected state, focus ring. */
    val Purple = Color(0xFF9D4EDD)

    /** Soft lavender: hover glow, highlights, gradient tail. */
    val Lavender = Color(0xFFC084FC)

    /** Deep violet used at the start of long gradients. */
    val Violet = Color(0xFF7928CA)

    /** Cool counterpoint for charts and tertiary accents. */
    val Indigo = Color(0xFF6366F1)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFE2E8F0)
    val TextMuted = Color(0xFF94A3B8)

    val Gold = Color(0xFFFFB703)
    val Success = Color(0xFF57E3A1)
    val Danger = Color(0xFFFF8A93)

    /** Pure-ish reader canvas so artwork is the only bright thing on screen. */
    val ReaderCanvas = Color(0xFF07080C)

    /** 10% violet halo used for selected and hovered surfaces. */
    val Glow = Color(0x1A9D4EDD)
}

// Short aliases kept for the desktop call sites that predate the token object.
val Canvas get() = YomiraColors.Oled
val Panel get() = YomiraColors.Surface
val Hairline get() = YomiraColors.Outline
val Ink get() = YomiraColors.TextPrimary
val InkMuted get() = YomiraColors.TextMuted
val Violet get() = YomiraColors.Purple
val Lavender get() = YomiraColors.Lavender
val Amber get() = YomiraColors.Gold

/** The single accent ramp: violet into lavender, never into hot pink. */
val BrandGradient: Brush
    get() = Brush.horizontalGradient(listOf(YomiraColors.Purple, YomiraColors.Lavender))

private val DesktopColorScheme = darkColorScheme(
    primary = YomiraColors.Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B1745),
    onPrimaryContainer = YomiraColors.TextPrimary,
    secondary = YomiraColors.Lavender,
    onSecondary = YomiraColors.Oled,
    secondaryContainer = Color(0xFF3B2452),
    onSecondaryContainer = YomiraColors.TextPrimary,
    tertiary = YomiraColors.Indigo,
    background = YomiraColors.Oled,
    onBackground = YomiraColors.TextPrimary,
    surface = YomiraColors.Surface,
    onSurface = YomiraColors.TextPrimary,
    surfaceVariant = YomiraColors.SurfaceRaised,
    onSurfaceVariant = YomiraColors.TextMuted,
    outline = YomiraColors.Outline,
    error = YomiraColors.Danger,
    onError = Color(0xFF3A0006),
)

private val Display = FontFamily.SansSerif
private val Body = FontFamily.SansSerif
private val Numerals = FontFamily.Monospace

private val DesktopTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Black,
        fontSize = 54.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.0).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
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
        letterSpacing = 0.3.sp,
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

/** Shared geometry so every surface uses the same 16dp card language. */
object YomiraShape {
    val Poster = RoundedCornerShape(16.dp)
    val Panel = RoundedCornerShape(20.dp)
    val Hero = RoundedCornerShape(24.dp)
    val Control = RoundedCornerShape(14.dp)
    val Capsule = RoundedCornerShape(50)
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
                    0.5f to Color(0xFF08090D),
                    1f to Color(0xFF06070A),
                ),
            )
            .drawBehind {
                // Brand glows scale with the window, so an ultrawide 4K canvas keeps
                // the same composition instead of pinning them to 1080p pixels.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(YomiraColors.Purple.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width * 0.88f, -size.height * 0.10f),
                        radius = size.width * 0.54f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(YomiraColors.Lavender.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width * 0.03f, size.height * 1.04f),
                        radius = size.width * 0.44f,
                    ),
                )
            },
    ) {
        content()
    }
}

/** Standard gutter of the desktop shell; scales with the window, not the display DPI. */
val ShellPadding = 32.dp

/** Horizontal gap between poster columns. */
val GridGutter = 20.dp

/** Vertical gap between poster rows: larger so the caption block breathes. */
val GridRowGap = 30.dp

/** Corner radius shared by poster cards, covers and chapter rows. */
val CardCorner = 16.dp
