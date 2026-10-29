package com.yomira.feature.details

import com.yomira.core.model.AdaptationKind
import com.yomira.core.model.WatchableAdaptation

/** Stable, compact summary; individual seasons remain in the full adaptation list. */
internal fun detailAdaptationKinds(adaptations: List<WatchableAdaptation>): List<AdaptationKind> =
    adaptations.map { it.kind }.distinct().sortedBy { it.ordinal }
