package com.yomira.feature.journey

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.yomira.domain.journey.BadgeTier
import com.yomira.domain.journey.WrappedSummary
import com.yomira.domain.journey.WrappedPeriod
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the shareable Wrapped card as a PNG and hands it to the system share
 * sheet.
 *
 * The card is drawn with the platform canvas instead of a composable screenshot:
 * it must be pixel-stable across devices, work while the story pager is at any
 * page, and never depend on the current window size. The file lives in the
 * app's cache directory and is exposed read-only through a FileProvider, so no
 * storage permission is needed and nothing remains in the gallery.
 */
object WrappedShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    suspend fun share(context: Context, summary: WrappedSummary, copy: WrappedCopy): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = render(summary, copy)
                val directory = File(context.cacheDir, "wrapped").apply { mkdirs() }
                val file = File(directory, "yomira-wrapped-${summary.period.name.lowercase()}-${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encode failed" }
                }
                bitmap.recycle()
                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "${copy.shareCardTag} · ${copy.periodLabel(summary)}",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri(copy.share, uri)
                }
                // Render and write happen off the main thread; the chooser itself
                // must be launched from the main looper.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching {
                        context.startActivity(Intent.createChooser(share, copy.shareChooser))
                    }
                }
                Unit
            }
        }

    /** Pure renderer; exposed for tests and previews. */
    fun render(summary: WrappedSummary, copy: WrappedCopy): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#12061F"), Color.parseColor("#3A0B33"), Color.parseColor("#08040F")),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), 0f,
                intArrayOf(Color.parseColor("#7A0B52"), Color.parseColor("#250A3A")),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(RectF(64f, 140f, WIDTH - 64f, HEIGHT - 180f), 56f, 56f, glow)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#140A22") }
        canvas.drawRoundRect(RectF(80f, 156f, WIDTH - 80f, HEIGHT - 196f), 48f, 48f, inner)

        val left = 128f
        var y = 290f
        val tag = textPaint(34f, Color.parseColor("#FF6FB1"), bold = true).apply { letterSpacing = .22f }
        canvas.drawText(copy.shareCardTag, left, y, tag)

        y += 80f
        canvas.drawText(copy.periodLabel(summary).uppercase(), left, y, textPaint(56f, Color.WHITE, bold = true))

        y += 70f
        canvas.drawText("YOMIRA WRAPPED", left, y, textPaint(30f, Color.parseColor("#B9A7D6")))

        y += 150f
        canvas.drawText(copy.formatNumber(summary.chapters), left, y, textPaint(190f, Color.parseColor("#FF2E93"), bold = true))
        y += 72f
        drawWrapped(
            canvas,
            if (summary.period == WrappedPeriod.YEARLY) copy.chaptersHeadlineYearly.format(summary.chapters)
            else copy.chaptersHeadline.format(summary.chapters),
            left, y, textPaint(46f, Color.WHITE, bold = true), WIDTH - left * 2,
        )

        y += 100f
        val rows = listOf(
            copy.timeHeadline.format(copy.formatDuration(summary.activeMillis)),
            copy.chaptersSubline.format(copy.formatNumber(summary.pages), summary.readingUnits),
            "${copy.topGenreHeadline}: ${summary.topGenre ?: "—"}",
            copy.levelHeadline.format(
                summary.level.level,
                if (copy.english) summary.level.titleEn else summary.level.titleTr,
            ),
        )
        val rowPaint = textPaint(38f, Color.parseColor("#E7DFF5"))
        rows.forEach { row ->
            drawWrapped(canvas, row, left, y, rowPaint, WIDTH - left * 2)
            y += 66f
        }

        y += 26f
        val growth = summary.growthPercent
        val growthText = when {
            growth == null -> copy.growthFirst
            growth >= 0 -> copy.growthUp.format(growth)
            else -> copy.growthDown.format(growth)
        }
        canvas.drawText(growthText, left, y, textPaint(44f, Color.parseColor("#55F0C1"), bold = true))

        if (summary.badgesEarned.isNotEmpty()) {
            y += 96f
            canvas.drawText(copy.badgesHeadline, left, y, textPaint(30f, Color.parseColor("#B9A7D6")))
            y += 56f
            var x = left
            summary.badgesEarned.distinct().take(3).forEach { tier ->
                val label = if (copy.english) tier.labelEn else tier.labelTr
                val paint = textPaint(30f, Color.WHITE, bold = true)
                val width = paint.measureText(label) + 56f
                if (x + width > WIDTH - left) return@forEach
                val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (tierArgb(tier) and 0x00FFFFFF) or 0x38000000
                }
                canvas.drawRoundRect(RectF(x, y - 42f, x + width, y + 14f), 28f, 28f, chip)
                canvas.drawText(label, x + 28f, y - 4f, paint)
                x += width + 18f
            }
        }

        canvas.drawText("yomira · local-first reading journey", left, HEIGHT - 320f, textPaint(28f, Color.parseColor("#8E7AA8")))

        return bitmap
    }

    private fun drawWrapped(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, maxWidth: Float) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        lines.take(2).forEachIndexed { index, line ->
            val suffix = if (index == 1 && lines.size > 2) "…" else ""
            canvas.drawText(line + suffix, x, y + index * 58f, paint)
        }
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun tierArgb(tier: BadgeTier): Int = when (tier) {
        BadgeTier.BRONZE -> 0xFFCD7F32.toInt()
        BadgeTier.SILVER -> 0xFFC9D1D9.toInt()
        BadgeTier.GOLD -> 0xFFFFB703.toInt()
        BadgeTier.PLATINUM -> 0xFF7DE2FF.toInt()
    }
}
