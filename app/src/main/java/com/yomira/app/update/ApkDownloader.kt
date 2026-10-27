package com.yomira.app.update

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Publishes the APK only after a complete response; failed partial files are never installed. */
class ApkDownloader(private val client: OkHttpClient = OkHttpClient.Builder().followSslRedirects(false).build()) {
    suspend fun download(url: String, directory: File, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        require(request.url.isHttps) { "Updates require HTTPS" }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create update directory" }
        val partial = File(directory, "yomira-update.apk.part")
        val target = File(directory, "yomira-update.apk")
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || !response.request.url.isHttps) throw IOException("Update download failed")
                val body = response.body ?: throw IOException("Empty update response")
                val length = body.contentLength()
                var received = 0L
                var lastPercent = -1
                onProgress(null)
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            received += count
                            if (length > 0) {
                                val percent = ((received * 100) / length).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    onProgress(percent / 100f)
                                    lastPercent = percent
                                }
                            }
                        }
                    }
                }
                if (received == 0L || (length >= 0 && received != length)) throw IOException("Incomplete update")
            }
            currentCoroutineContext().ensureActive()
            if (target.exists() && !target.delete()) throw IOException("Cannot replace update")
            if (!partial.renameTo(target)) throw IOException("Cannot publish update")
            target
        } finally {
            partial.delete()
        }
    }
}
