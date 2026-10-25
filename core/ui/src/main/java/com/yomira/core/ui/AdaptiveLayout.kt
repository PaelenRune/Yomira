package com.yomira.core.ui

import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** Compact = phone, Medium = tablet/foldable, Expanded = large tablet/desktop. */
enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * Window width buckets used by the adaptive navigation shell.
 *
 * The breakpoints follow the Material adaptive guidance and the product brief:
 *  - `COMPACT`  (< 600dp): phones; one pane, bottom navigation.
 *  - `MEDIUM`   (600–839dp): tablets and unfolded foldables; navigation rail,
 *    two-pane detail (chapter list + series dossier).
 *  - `EXPANDED` (>= 840dp): large tablets and desktop-sized windows; rail,
 *    two-pane detail and a wider catalogue grid.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

enum class AdaptiveNavigation {
    BOTTOM_BAR,
    NAVIGATION_RAIL,
}

/**
 * Pure layout decision for one window width. No Compose types are involved, so
 * the phone/tablet contract can be unit tested on the JVM.
 */
data class AdaptiveLayoutInfo(
    val widthDp: Float,
    val widthClass: WindowWidthClass,
    val navigation: AdaptiveNavigation,
    val twoPane: Boolean,
    val catalogueColumns: Int,
    val detailListPaneWidthDp: Int,
) {
    val useNavigationRail: Boolean get() = navigation == AdaptiveNavigation.NAVIGATION_RAIL
    val useBottomBar: Boolean get() = navigation == AdaptiveNavigation.BOTTOM_BAR
}

/**
 * Auto-detection of phone vs tablet/foldable by available width.
 *
 * `twoPane` starts exactly at the tablet breakpoint (600dp), so a landscape
 * phone stays single-pane while every tablet-class window gets the chapter list
 * beside the cover/credits/synopsis dossier.
 */
object AdaptiveLayout {
    const val COMPACT_MAX_WIDTH_DP = 599.9f
    const val EXPANDED_MIN_WIDTH_DP = 840f

    /** The product's tablet breakpoint: rail + 2-pane from 600dp upwards. */
    const val TABLET_MIN_WIDTH_DP = 600f

    fun forWidth(widthDp: Float): AdaptiveLayoutInfo {
        val width = if (!widthDp.isFinite() || widthDp < 0f) 0f else widthDp
        val widthClass = when {
            width < TABLET_MIN_WIDTH_DP -> WindowWidthClass.COMPACT
            width < EXPANDED_MIN_WIDTH_DP -> WindowWidthClass.MEDIUM
            else -> WindowWidthClass.EXPANDED
        }
        val tablet = widthClass != WindowWidthClass.COMPACT
        return AdaptiveLayoutInfo(
            widthDp = width,
            widthClass = widthClass,
            navigation = if (tablet) AdaptiveNavigation.NAVIGATION_RAIL else AdaptiveNavigation.BOTTOM_BAR,
            twoPane = tablet,
            catalogueColumns = when (widthClass) {
                WindowWidthClass.COMPACT -> if (width >= 420f) 3 else 2
                WindowWidthClass.MEDIUM -> 3
                WindowWidthClass.EXPANDED -> 4
            },
            detailListPaneWidthDp = when (widthClass) {
                WindowWidthClass.COMPACT -> 0
                // Keep the chapter spine readable but always leave the dossier
                // at least ~300dp on a 600dp tablet.
                WindowWidthClass.MEDIUM -> (width * 0.42f).roundToInt().coerceIn(280, 340)
                WindowWidthClass.EXPANDED -> (width * 0.36f).roundToInt().coerceIn(320, 420)
            },
        )
    }

    /** Material width class of the live window. */
    fun windowSizeClass(width: Dp): WindowSizeClass = when {
        !width.value.isFinite() || width.value < TABLET_MIN_WIDTH_DP -> WindowSizeClass.Compact
        width.value < EXPANDED_MIN_WIDTH_DP -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }

    /** True from 600dp upwards: tablets and unfolded foldables get the rail. */
    fun usesNavigationRail(widthDp: Float): Boolean = forWidth(widthDp).useNavigationRail

    /** True from 600dp upwards: detail switches to list-left / dossier-right. */
    fun prefersTwoPane(widthDp: Float): Boolean = forWidth(widthDp).twoPane

    /**
     * Grid columns for a width class, honouring the user's 2/3/4 preference but
     * never exceeding what [MangaCard] can render legibly.
     */
    fun catalogColumns(sizeClass: WindowSizeClass, userPreference: Int): Int = when (sizeClass) {
        WindowSizeClass.Compact -> userPreference.coerceIn(2, 3)
        WindowSizeClass.Medium -> userPreference.coerceIn(3, 4)
        WindowSizeClass.Expanded -> 4
    }
}
