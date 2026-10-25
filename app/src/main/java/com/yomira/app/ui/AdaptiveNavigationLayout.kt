package com.yomira.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yomira.core.ui.CatalogCopy
import com.yomira.core.ui.MainDestination
import com.yomira.core.ui.YomiraBottomNavBar
import com.yomira.core.ui.YomiraNavigationRail

/**
 * Layout buckets of the phone / tablet / foldable shell.
 *
 * The numeric contract is deliberately tiny and pure so it can be unit-tested
 * without an emulator: phones below 600dp get one pane and a bottom bar; every
 * window at or above 600dp gets the left rail and two panes.
 */
enum class AdaptiveNavigationMode {
    /** Phones: one pane, bottom navigation. */
    COMPACT,

    /** Small tablets and unfolded foldables: rail + master-detail. */
    MEDIUM,

    /** Large tablets and desktop-class windows: rail + roomier master-detail. */
    EXPANDED,
}

/**
 * Single source of truth for the adaptive breakpoints.
 *
 * Material's window size classes put the compact/medium line at 600dp and the
 * medium/expanded line at 840dp. Yomira's tablet experience starts at 600dp, so
 * [usesNavigationRail] and [usesTwoPaneDetail] share exactly that line.
 */
object AdaptiveNavigationPolicy {
    const val COMPACT_MAX_WIDTH_DP = 599.9f
    const val MEDIUM_MIN_WIDTH_DP = 600f
    const val EXPANDED_MIN_WIDTH_DP = 840f

    fun modeFor(widthDp: Float): AdaptiveNavigationMode = when {
        widthDp >= EXPANDED_MIN_WIDTH_DP -> AdaptiveNavigationMode.EXPANDED
        widthDp >= MEDIUM_MIN_WIDTH_DP -> AdaptiveNavigationMode.MEDIUM
        else -> AdaptiveNavigationMode.COMPACT
    }

    /** Phones keep the bottom bar; tablets and foldables navigate from the left rail. */
    fun usesNavigationRail(widthDp: Float): Boolean = modeFor(widthDp) != AdaptiveNavigationMode.COMPACT

    /** The complementary rule, useful for tests and for asserting one navigation affordance. */
    fun usesBottomNavigationBar(widthDp: Float): Boolean = modeFor(widthDp) == AdaptiveNavigationMode.COMPACT

    /** One pane for phones, two panes (chapters | identity) from 600dp up. */
    fun paneCount(widthDp: Float): Int = if (usesNavigationRail(widthDp)) 2 else 1

    /** Whether the details screen should render master-detail instead of one scroll. */
    fun usesTwoPaneDetail(widthDp: Float): Boolean = paneCount(widthDp) == 2

    /** Poster grid density suggested to the catalog screen. */
    fun catalogColumns(widthDp: Float, preferred: Int): Int = when (modeFor(widthDp)) {
        AdaptiveNavigationMode.COMPACT -> 1
        AdaptiveNavigationMode.MEDIUM -> preferred.coerceIn(2, 4)
        AdaptiveNavigationMode.EXPANDED -> preferred.coerceIn(4, 6)
    }
}

/**
 * Adaptive app shell shared by every top-level destination.
 *
 * The caller supplies the already-built top bar/snackbar host and the NavHost
 * content. At >=600dp the bottom bar is replaced by [YomiraNavigationRail], so a
 * tablet never shows two navigation affordances at the same time. Hidden Vault
 * entries are filtered by the shared navigation components, not duplicated here.
 */
@Composable
fun AdaptiveNavigationLayout(
    selectedRoute: String?,
    onSelect: (MainDestination) -> Unit,
    copy: CatalogCopy,
    vaultVisible: Boolean,
    showNavigation: Boolean = true,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = AdaptiveNavigationPolicy.usesNavigationRail(maxWidth.value)
        if (rail) {
            Row(Modifier.fillMaxSize()) {
                if (showNavigation) {
                    YomiraNavigationRail(
                        selected = selectedRoute,
                        onSelect = onSelect,
                        copy = copy,
                        vaultVisible = vaultVisible,
                    )
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    topBar = topBar,
                    snackbarHost = snackbarHost,
                ) { padding -> content(padding) }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxWidth(),
                topBar = topBar,
                snackbarHost = snackbarHost,
                bottomBar = {
                    if (showNavigation) {
                        YomiraBottomNavBar(
                            selected = selectedRoute,
                            onSelect = onSelect,
                            copy = copy,
                            vaultVisible = vaultVisible,
                        )
                    }
                },
            ) { padding -> content(padding) }
        }
    }
}

/**
 * Reusable master-detail body for the series screen.
 *
 * On phones the existing single-scroll detail is rendered untouched. On tablets
 * the chapter list becomes the left pane (42%) and the cover/metadata/synopsis
 * the right pane (58%), separated by a one-pixel hairline. Both panes own their
 * scroll state, so browsing a long chapter list never hides the summary.
 */
@Composable
fun AdaptiveTwoPaneDetail(
    compactContent: @Composable () -> Unit,
    chapterPane: @Composable () -> Unit,
    identityPane: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (AdaptiveNavigationPolicy.usesTwoPaneDetail(maxWidth.value)) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(.42f).fillMaxHeight()) { chapterPane() }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF50495F).copy(alpha = .35f)))
                Box(Modifier.weight(.58f).fillMaxHeight()) { identityPane() }
            }
        } else {
            compactContent()
        }
    }
}
