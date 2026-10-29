package com.yomira.app.extensions

/** Bounded ICO container reader: modern PNG frames and common 32-bit Windows favicon DIBs. */
internal object IcoImage {
    const val MAX_BYTES = 1_048_576L
    sealed interface Frame {
        data class Png(val bytes: ByteArray) : Frame
        data class Argb(val width: Int, val height: Int, val pixels: IntArray) : Frame
    }

    fun read(bytes: ByteArray): Frame? = runCatching {
        if (bytes.size > MAX_BYTES || bytes.size < 6 || bytes.u16(0) != 0 || bytes.u16(2) != 1) return null
        val count = bytes.u16(4)
        if (count !in 1..256 || bytes.size < 6 + count * 16) return null
        (0 until count).map { 6 + it * 16 }.sortedByDescending { entry ->
            (bytes[entry].toInt() and 255).let { if (it == 0) 256 else it } *
                (bytes[entry + 1].toInt() and 255).let { if (it == 0) 256 else it }
        }.firstNotNullOfOrNull { entry ->
            val size = bytes.u32(entry + 8)
            val offset = bytes.u32(entry + 12)
            if (size <= 0 || offset < 6 + count * 16 || offset + size > bytes.size) null
            else frame(bytes.copyOfRange(offset.toInt(), (offset + size).toInt()))
        }
    }.getOrNull()

    private fun frame(data: ByteArray): Frame? {
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        if (data.size >= 8 && data.copyOfRange(0, 8).contentEquals(png)) return Frame.Png(data)
        if (data.size < 40) return null
        val header = data.u32(0)
        val width = data.u32(4).toInt()
        val doubledHeight = data.u32(8).toInt()
        val height = doubledHeight / 2
        if (header < 40 || header > data.size || width !in 1..256 || height !in 1..256 ||
            doubledHeight != height * 2 || data.u16(12) != 1 || data.u16(14) != 32 || data.u32(16) != 0L) return null
        val start = header.toInt()
        val end = start + width * height * 4
        if (end > data.size) return null
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            data.u32(start + ((height - 1 - y) * width + x) * 4).toInt()
        }
        val hasAlpha = pixels.any { it ushr 24 != 0 }
        val maskStride = ((width + 31) / 32) * 4
        val hasMask = end + maskStride * height <= data.size
        pixels.indices.forEach { index ->
            val x = index % width
            val y = index / width
            val masked = hasMask && (data[end + (height - 1 - y) * maskStride + x / 8].toInt() and
                (128 ushr (x % 8))) != 0
            if (masked) pixels[index] = pixels[index] and 0x00ffffff
            else if (!hasAlpha) pixels[index] = pixels[index] or (255 shl 24)
        }
        return Frame.Argb(width, height, pixels)
    }

    private fun ByteArray.u16(offset: Int): Int = (this[offset].toInt() and 255) or
        ((this[offset + 1].toInt() and 255) shl 8)
    private fun ByteArray.u32(offset: Int): Long = u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)
}
