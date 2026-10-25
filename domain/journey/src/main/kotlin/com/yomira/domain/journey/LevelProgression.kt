package com.yomira.domain.journey

import kotlin.math.max

/**
 * Level curve and the "Seviye 12 · Fantastik Gezgin" identity.
 *
 * The curve is quadratic but gentle early on: level 2 at 160 XP, level 12 at
 * 6.160 XP. Titles change every few levels so the profile card always says
 * something more personal than a bare number.
 */
data class LevelState(
    val level: Int,
    val titleTr: String,
    val titleEn: String,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
) {
    val progress: Float
        get() = when {
            level >= LevelProgression.MAX_LEVEL -> 1f
            xpForNextLevel <= 0 -> 1f
            else -> (xpIntoLevel.toFloat() / xpForNextLevel).coerceIn(0f, 1f)
        }
    val xpRemaining: Int
        get() = if (level >= LevelProgression.MAX_LEVEL) 0 else (xpForNextLevel - xpIntoLevel).coerceAtLeast(0)
}

object LevelProgression {
    const val MAX_LEVEL = 60

    /** Cumulative XP needed to *reach* [level]. Level 1 starts at zero. */
    fun xpRequiredForLevel(level: Int): Int {
        val safe = level.coerceIn(1, MAX_LEVEL + 1)
        if (safe <= 1) return 0
        val n = safe - 1
        return n * n * 40 + n * 120
    }

    fun levelForXp(totalXp: Int): Int {
        val xp = totalXp.coerceAtLeast(0)
        var level = 1
        while (level < MAX_LEVEL && xp >= xpRequiredForLevel(level + 1)) level++
        return level
    }

    fun stateForXp(totalXp: Int): LevelState {
        val xp = totalXp.coerceAtLeast(0)
        val level = levelForXp(xp)
        val start = xpRequiredForLevel(level)
        val next = xpRequiredForLevel(minOf(level + 1, MAX_LEVEL + 1))
        val title = titleForLevel(level)
        return LevelState(
            level = level,
            titleTr = title.first,
            titleEn = title.second,
            totalXp = xp,
            xpIntoLevel = max(0, xp - start),
            xpForNextLevel = max(1, next - start),
        )
    }

    fun titleForLevel(level: Int): Pair<String, String> = when {
        level >= 40 -> "Yomira Efsanesi" to "Yomira Legend"
        level >= 30 -> "Yomira Ustası" to "Yomira Master"
        level >= 20 -> "Zaman Yolcusu" to "Time Traveler"
        level >= 15 -> "Efsane Avcısı" to "Legend Hunter"
        level >= 10 -> "Fantastik Gezgin" to "Fantasy Voyager"
        level >= 5 -> "Kütüphane Gezgini" to "Library Wanderer"
        else -> "Meraklı Çırak" to "Curious Apprentice"
    }
}
