package com.yomira.core.model

import kotlin.math.ln

/** Consensus ranking; missing counts stay neutral rather than inventing reader numbers. */
fun Manga.globalRankScore(maxPopularity: Long, freshness: Double = 0.0): Double {
    val popNorm = if (maxPopularity > 0) {
        ln(1.0 + (popularity ?: 0L)) / ln(1.0 + maxPopularity)
    } else {
        0.0
    }
    return popNorm * 0.55 + (score ?: 7.0) / 10.0 * 0.35 + freshness * 0.10
}
