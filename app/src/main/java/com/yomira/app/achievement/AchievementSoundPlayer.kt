package com.yomira.app.achievement

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Original C-major chime; notification audio routing respects volume and silent mode. */
class AchievementSoundPlayer(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun haptic(view: View) {
        // View feedback honors the user's system haptic setting and needs no permission.
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS,
        )
    }

    suspend fun play() = withContext(Dispatchers.Default) {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return@withContext
        val rate = 22_050
        val durationMillis = 900L
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val samples = ShortArray((rate * durationMillis / 1000).toInt()) { index ->
            val time = index.toDouble() / rate
            val value = notes.indices.sumOf { note ->
                val age = time - note * 0.12
                if (age < 0) 0.0 else {
                    val attack = (age / 0.012).coerceAtMost(1.0)
                    val release = ((0.9 - time) / 0.08).coerceIn(0.0, 1.0)
                    sin(2 * PI * notes[note] * age) * exp(-age * 7) * attack * release
                }
            }
            (value * 6000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        } catch (_: RuntimeException) {
            return@withContext // Audio service unavailable: the visual celebration still works.
        }
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED && track.write(samples, 0, samples.size) == samples.size) {
                track.play()
                delay(durationMillis)
            }
        } catch (_: IllegalStateException) {
            // Audio devices may disappear while the app is backgrounded.
        } finally {
            track.release()
        }
    }
}
