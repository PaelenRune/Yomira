package com.yomira.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Yomira v5 palette: OLED-first near-black, layered plum surfaces and a
 * neon violet → lavender accent ramp. The neutrals are deliberately cool
 * (blue-leaning) so artwork keeps the contrast it was drawn with instead of
 * picking up a warm cast from the chrome.
 */

// --- Backgrounds & surfaces -------------------------------------------------
/** OLED-friendly app canvas. True black is avoided so hairline borders still read. */
val DeepOledBlack = Color(0xFF090A0E)

/** Cards, sheets and the floating navigation chrome. */
val CardSurface = Color(0xFF13141C)

/** Raised controls, chips and nested card tone. */
val SurfaceRaised = Color(0xFF1B1C26)

/** One step above variant: menus, pressed cards, badge fills. */
val SurfaceHighest = Color(0xFF23242F)

// --- Accent ramp ------------------------------------------------------------
/** Canlı mor — primary action, selected state, brand anchor. */
val NeonPurple = Color(0xFF9D4EDD)

/** Yumuşak lavanta — secondary accent, highlights and soft emphasis. */
val NeonLavender = Color(0xFFC084FC)

/**
 * Legacy accent name imported by the feature modules. It now resolves to the
 * palette's soft lavender so secondary surfaces (vault, streaks, badges) stay
 * inside the violet ramp instead of reintroducing hot pink.
 */
val NeonFuchsia = NeonLavender

/** Indigo — cool counterpoint for charts, vault and tertiary actions. */
val NeonIndigo = Color(0xFF6366F1)

/** Deep violet — gradient tail and pressed accents. */
val NeonViolet = Color(0xFF7928CA)

// --- Typography -------------------------------------------------------------
val TextPrimary = Color(0xFFFFFFFF)

/** Softer headline/body ink; easier on the eyes for long synopsis blocks. */
val TextSecondary = Color(0xFFE2E8F0)

/** Muted metadata, timestamps, captions. */
val TextMuted = Color(0xFF94A3B8)

val RatingGold = Color(0xFFFFB703)

// --- Lines ------------------------------------------------------------------
/** 1dp elegant card border on the dark plum surface stack. */
val HairlineBorder = Color(0xFF262738)

/** 10% violet halo used for selected/featured borders. */
val PurpleGlow = Color(0x1A9D4EDD)

// --- Supporting scheme slots ------------------------------------------------
internal val PurpleContainer = Color(0xFF2B1745)
internal val LavenderContainer = Color(0xFF3B2452)
internal val IndigoContainer = Color(0xFF1E1B4B)
internal val Outline = Color(0xFF3A3B4C)
internal val Error = Color(0xFFFFB4AB)
internal val ErrorContainer = Color(0xFF93000A)
