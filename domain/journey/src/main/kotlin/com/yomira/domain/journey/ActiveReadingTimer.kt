package com.yomira.domain.journey

/**
 * Pure active-time guard for reader sessions.
 *
 * The clock advances only between two interactions closer than
 * [inactivityTimeoutMillis]. A phone left open on a page therefore stops
 * counting after 60 seconds instead of inflating the monthly total. Backgrounding
 * the app calls [pause], which drops the anchor without crediting the gap.
 */
class ActiveReadingTimer(
    private val inactivityTimeoutMillis: Long = DEFAULT_INACTIVITY_TIMEOUT_MILLIS,
) {
    private var lastInteractionAt: Long? = null
    private var accumulatedMillis: Long = 0L

    fun start(nowMillis: Long) {
        lastInteractionAt = nowMillis
    }

    /** @return active milliseconds credited for this interaction (0 when idle). */
    fun interaction(nowMillis: Long): Long {
        val previous = lastInteractionAt
        lastInteractionAt = nowMillis
        if (previous == null) {
            // Resuming after a pause: the gap is not reading time, but the time
            // already accumulated before the pause is preserved.
            return 0L
        }
        val delta = (nowMillis - previous).coerceAtLeast(0L)
        if (delta > inactivityTimeoutMillis) return 0L
        accumulatedMillis += delta
        return delta
    }

    /** Reads and resets the unflushed active duration. */
    fun drainActiveMillis(): Long {
        val value = accumulatedMillis
        accumulatedMillis = 0L
        return value
    }

    /** Non-destructive read used to decide when a cheap periodic flush is due. */
    fun pendingMillis(): Long = accumulatedMillis

    fun pause() {
        lastInteractionAt = null
    }

    fun reset() {
        lastInteractionAt = null
        accumulatedMillis = 0L
    }

    companion object {
        const val DEFAULT_INACTIVITY_TIMEOUT_MILLIS = 60_000L
    }
}
