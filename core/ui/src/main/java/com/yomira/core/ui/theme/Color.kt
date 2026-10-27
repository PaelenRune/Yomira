package com.yomira.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OLED manga-gallery palette: quiet near-black surfaces, electric purple
 * actions and pink highlights. Artwork, not chrome, owns the shelf.
 */

// --- Backgrounds & surfaces -------------------------------------------------
/** True OLED black: unlike near-black, unlit background pixels draw no emission power. */
val DeepOledBlack = Color(0xFF000000)

/** Cards, sheets and the floating navigation chrome. */
val CardSurface = Color(0xFF16161E)

/** Raised controls, chips and nested card tone. */
val SurfaceRaised = Color(0xFF1E1E29)

/** One step above variant: menus, pressed cards, badge fills. */
val SurfaceHighest = Color(0xFF282834)

// --- Accent ramp ------------------------------------------------------------
/** Canlı mor — primary action, selected state, brand anchor. */
val NeonPurple = Color(0xFF7B61FF)

/** Yumuşak lavanta — secondary accent, highlights and soft emphasis. */
val NeonLavender = Color(0xFFC084FC)

/** Electric pink counterpoint for highlights and secondary actions. */
val NeonFuchsia = Color(0xFFFF3366)

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
/** Dividers and outlined controls; poster cards remain borderless. */
val HairlineBorder = Color(0xFF262738)

/** 10% violet halo used for selected/featured borders. */
val PurpleGlow = Color(0x1A7B61FF)

// --- Supporting scheme slots ------------------------------------------------
internal val PurpleContainer = Color(0xFF292044)
internal val PinkContainer = Color(0xFF451525)
internal val LavenderContainer = Color(0xFF3B2452)
internal val IndigoContainer = Color(0xFF1E1B4B)
internal val Outline = Color(0xFF3A3B4C)
internal val Error = Color(0xFFFFB4AB)
internal val ErrorContainer = Color(0xFF93000A)
