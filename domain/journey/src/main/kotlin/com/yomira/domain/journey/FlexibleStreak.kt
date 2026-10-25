package com.yomira.domain.journey

/**
 * Flexible streak rule for "Alev Serisi".
 *
 * The product requirement is explicit: the streak must not punish the user.
 * A streak therefore earns **one rest day per rolling seven-day window**. If
 * the user skips two days in the same week, the streak breaks; if life happens
 * once, the flame survives. The current day itself is never punished before it
 * ends: reading yesterday is enough to keep the streak alive this morning.
 */
object FlexibleStreak {
    const val GRACE_DAYS_PER_WEEK = 1

    data class Result(
        val current: Int,
        val longest: Int,
        val graceAvailable: Boolean,
    )

    fun evaluate(activeDays: Set<Long>, todayEpochDay: Long): Result {
        if (activeDays.isEmpty()) return Result(0, 0, true)
        val current = current(activeDays, todayEpochDay)
        return Result(current, longest(activeDays), graceAvailable(activeDays, todayEpochDay))
    }

    fun current(activeDays: Set<Long>, todayEpochDay: Long): Int {
        if (activeDays.isEmpty()) return 0
        var cursor = when {
            todayEpochDay in activeDays -> todayEpochDay
            (todayEpochDay - 1) in activeDays -> todayEpochDay - 1
            else -> return 0
        }
        val firstDay = activeDays.min()
        var streak = 0
        var grace = GRACE_DAYS_PER_WEEK
        var windowDays = 0
        while (cursor >= firstDay) {
            val active = cursor in activeDays
            if (active) {
                streak++
                windowDays++
            } else {
                if (streak == 0) break
                if (grace <= 0) break
                grace--
                windowDays++
            }
            if (windowDays >= 7) {
                windowDays = 0
                grace = GRACE_DAYS_PER_WEEK
            }
            cursor--
        }
        return streak
    }

    fun longest(activeDays: Set<Long>): Int {
        if (activeDays.isEmpty()) return 0
        var cursor = activeDays.min()
        val lastDay = activeDays.max()
        var best = 0
        var streak = 0
        var grace = GRACE_DAYS_PER_WEEK
        var windowDays = 0
        while (cursor <= lastDay) {
            if (cursor in activeDays) {
                streak++
                windowDays++
            } else if (streak > 0 && grace > 0) {
                grace--
                windowDays++
            } else {
                best = maxOf(best, streak)
                streak = 0
                grace = GRACE_DAYS_PER_WEEK
                windowDays = 0
            }
            if (windowDays >= 7) {
                windowDays = 0
                grace = GRACE_DAYS_PER_WEEK
            }
            best = maxOf(best, streak)
            cursor++
        }
        return best
    }

    fun graceAvailable(activeDays: Set<Long>, todayEpochDay: Long): Boolean {
        if (activeDays.isEmpty()) return true
        val anchor = if (todayEpochDay in activeDays) todayEpochDay else todayEpochDay - 1
        var cursor = anchor
        var sinceWindowStart = 0
        while (cursor >= activeDays.min() && sinceWindowStart < 7) {
            if (cursor !in activeDays) return false
            cursor--
            sinceWindowStart++
        }
        return true
    }
}
