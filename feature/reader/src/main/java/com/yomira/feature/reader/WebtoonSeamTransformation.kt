package com.yomira.feature.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Coil transformation that collapses the blank bands and compression hairlines
 * a webtoon splitter leaves between two strips.
 *
 * [WebtoonSeamCleaner] only needs a down-sampled luminance grid, so the sample is
 * drawn into a scratch buffer from [HardwareBitmapPool]: scrolling a long strip
 * then never allocates a multi-megabyte bitmap just to measure a few rows.
 */
internal class WebtoonSeamTransformation(
    private val pool: HardwareBitmapPool,
    private val config: WebtoonSeamConfig = WebtoonSeamConfig(),
) : Transformation {

    override val cacheKey: String = "webtoon-seam-${config.blankThreshold}-${config.maxTrimFraction}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width <= 0 || input.height <= 0) return input
        val trim = measure(input)
        if (trim.total <= 0 || trim.top + trim.total >= input.height) return input
        // The trimmed strips are painted from a single continuous column, so the
        // cropped output keeps the artwork's own width and pixel alignment.
        return Bitmap.createBitmap(input, 0, trim.top, input.width, input.height - trim.total)
    }

    private fun measure(input: Bitmap): TrimSpec {
        val sampleWidth = min(input.width, MAX_SAMPLE_WIDTH).coerceAtLeast(1)
        val sampleHeight = (input.height.toFloat() * sampleWidth / input.width)
            .toInt().coerceIn(1, MAX_SAMPLE_HEIGHT)
        val sampleTrim = pool.withBuffer(sampleWidth, sampleHeight) { buffer ->
            val canvas = Canvas(buffer)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(
                input,
                Rect(0, 0, input.width, input.height),
                Rect(0, 0, sampleWidth, sampleHeight),
                paint,
            )
            val pixels = IntArray(sampleWidth * sampleHeight)
            buffer.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            val luminances = IntArray(pixels.size) { index ->
                val pixel = pixels[index]
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                (0.299f * red + 0.587f * green + 0.114f * blue).toInt().coerceIn(0, 255)
            }
            WebtoonSeamCleaner.trimFor(
                LuminanceGrid.fromLuminances(sampleWidth, sampleHeight, luminances),
                config,
            )
        }
        // The cleaner reports sample rows; the crop runs on the full-resolution
        // strip, so scale the band back before touching the bitmap.
        val verticalScale = input.height.toFloat() / sampleHeight
        return TrimSpec(
            top = (sampleTrim.top * verticalScale).roundToInt(),
            bottom = (sampleTrim.bottom * verticalScale).roundToInt(),
        )
    }

    private companion object {
        const val MAX_SAMPLE_WIDTH = 240
        /** Histogram-equivalent precision for row blanks needs no more rows. */
        const val MAX_SAMPLE_HEIGHT = 4096
    }
}
