package com.yomira.app.extensions

import org.junit.Assert.*
import org.junit.Test

class IcoImageTest {
    private fun ico(frame: ByteArray): ByteArray = ByteArray(22 + frame.size).also {
        it[2] = 1; it[4] = 1; it[6] = 1; it[7] = 1
        put32(it, 14, frame.size); put32(it, 18, 22)
        frame.copyInto(it, 22)
    }
    private fun put32(bytes: ByteArray, offset: Int, value: Int) {
        for (index in 0..3) bytes[offset + index] = (value ushr (index * 8)).toByte()
    }
    @Test fun pngFrameExtractsWithoutLosingBytes() {
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1)
        assertArrayEquals(png, (IcoImage.read(ico(png)) as IcoImage.Frame.Png).bytes)
    }
    @Test fun dibConvertsBgraAndLegacyZeroAlpha() {
        val dib = ByteArray(48)
        put32(dib, 0, 40); put32(dib, 4, 1); put32(dib, 8, 2)
        dib[12] = 1; dib[14] = 32
        dib[40] = 0x33; dib[41] = 0x22; dib[42] = 0x11
        val frame = IcoImage.read(ico(dib)) as IcoImage.Frame.Argb
        assertEquals(1, frame.width)
        assertEquals(1, frame.height)
        assertEquals(0xff112233.toInt(), frame.pixels.single())
        dib[44] = 0x80.toByte()
        assertEquals(0x00112233, (IcoImage.read(ico(dib)) as IcoImage.Frame.Argb).pixels.single())
    }
    @Test fun truncatedOrOversizedContainersAreRejected() {
        assertNull(IcoImage.read(byteArrayOf(0, 0, 1, 0)))
        assertNull(IcoImage.read(ByteArray(IcoImage.MAX_BYTES.toInt() + 1)))
        val truncated = ico(ByteArray(8))
        put32(truncated, 14, Int.MAX_VALUE)
        assertNull(IcoImage.read(truncated))
    }
    @Test fun oversizedBitmapDimensionsAreRejectedBeforeAllocation() {
        val dib = ByteArray(48)
        put32(dib, 0, 40); put32(dib, 4, Int.MAX_VALUE); put32(dib, 8, Int.MAX_VALUE)
        dib[12] = 1; dib[14] = 32
        assertNull(IcoImage.read(ico(dib)))
    }
}
