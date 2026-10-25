package com.yomira.feature.reader

import android.graphics.Bitmap
import java.util.ArrayDeque

/**
 * A small recycle pool for the scratch bitmaps the webtoon pipeline decodes and
 * re-draws while cleaning seams.
 *
 * Coil owns the bitmaps Compose draws — those are never recycled here. This pool
 * only serves *scratch* buffers created and consumed inside one measurement or
 * stitch step, which is what makes 120Hz scrolling free of GC hitches: instead
 * of allocating a multi-megabyte bitmap per strip, the same few buffers are
 * reused and released back.
 */
class HardwareBitmapPool(private val maxBuffers: Int = 4) {
    private val free = ArrayDeque<Bitmap>()
    private var allocated = 0

    /** Acquires an ARGB_8888 buffer of at least [width] x [height]. */
    @Synchronized
    fun acquire(width: Int, height: Int): Bitmap {
        require(width > 0 && height > 0) { "Buffer dimensions must be positive" }
        val iterator = free.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (candidate.isRecycled) {
                iterator.remove()
                allocated--
                continue
            }
            if (candidate.width >= width && candidate.height >= height) {
                iterator.remove()
                return candidate
            }
        }
        allocated++
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    /** Returns a scratch buffer for reuse. Recycled bitmaps are ignored. */
    @Synchronized
    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (free.size >= maxBuffers) {
            bitmap.recycle()
            allocated--
            return
        }
        free.addLast(bitmap)
    }

    /** Drops every pooled buffer; called when the app is asked to trim memory. */
    @Synchronized
    fun trim() {
        while (free.isNotEmpty()) {
            val bitmap = free.pollFirst() ?: break
            if (!bitmap.isRecycled) bitmap.recycle()
            allocated--
        }
    }

    /** Live buffer count; useful for leak checks in tests. */
    @Synchronized
    fun size(): Int = allocated

    /** Runs [block] with a scratch buffer and always releases it back. */
    inline fun <T> withBuffer(width: Int, height: Int, block: (Bitmap) -> T): T {
        val buffer = acquire(width, height)
        try {
            return block(buffer)
        } finally {
            release(buffer)
        }
    }
}