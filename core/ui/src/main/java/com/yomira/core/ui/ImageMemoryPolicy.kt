package com.yomira.core.ui

/** Separate, bounded LRU budgets for artwork and the short-lived reader loader. */
object ImageMemoryPolicy {
    private const val MIB = 1024L * 1024L

    fun artworkBytes(heapBytes: Long): Int = budget(heapBytes, 10, 32 * MIB)
    fun readerBytes(heapBytes: Long): Int = budget(heapBytes, 15, 64 * MIB)

    private fun budget(heapBytes: Long, percent: Int, cap: Long): Int =
        ((heapBytes.coerceAtLeast(0) / 100) * percent).coerceAtMost(cap).toInt()
}
