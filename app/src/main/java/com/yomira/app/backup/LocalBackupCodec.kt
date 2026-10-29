package com.yomira.app.backup

import com.yomira.core.database.entity.MangaEntity
import com.yomira.core.model.ContentRating
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import com.yomira.data.sync.SyncPayload
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Gzipped legacy Tachiyomi JSON, with a versioned Yomira projection for lossless local restore. */
internal object LocalBackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= MAX_BYTES)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    fun filename(now: LocalDateTime = LocalDateTime.now()) =
        "yomira-backup-${now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.tachibk"

    fun encode(backup: LocalBackup): ByteArray {
        backup.validate()
        val root = buildJsonObject {
            put("yomira", json.encodeToJsonElement(backup))
            putJsonArray("mangas") {
                backup.metadata.forEach { row -> add(buildJsonObject {
                    put("source", row.sourceId.toLongOrNull() ?: 0L)
                    put("url", row.id.substringAfter(':'))
                    put("title", row.title)
                    row.coverUrl?.let { put("thumbnail_url", it) }
                    put("favorite", true)
                }) }
            }
        }
        val plain = json.encodeToString(root).toByteArray()
        require(plain.size <= MAX_BYTES)
        return ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(plain) }
        }.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    /** Null means a foreign backup; its parsed document goes to TachiyomiBackupImporter. */
    fun decodeNative(bytes: ByteArray): LocalBackup? {
        require(bytes.size <= MAX_BYTES)
        val plain = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream()).use(::readBounded)
        } else bytes
        require(plain.size <= MAX_BYTES)
        if (plain.firstOrNull { !it.toInt().toChar().isWhitespace() } != '{'.code.toByte()) return null
        val root = json.parseToJsonElement(plain.decodeToString()).jsonObject
        val native = root["yomira"] ?: return null
        return json.decodeFromJsonElement<LocalBackup>(native).also { it.validate() }
    }
}

@Serializable
internal data class LocalBackup(val version: Int = 1, val metadata: List<BackupMetadata>, val state: SyncPayload) {
    fun validate() {
        require(version == 1)
        state.validate()
        require(metadata.map { it.id }.distinct().size == metadata.size)
        metadata.forEach { row ->
            require(row.id.isNotBlank() && row.title.isNotBlank() && row.sourceId.isNotBlank())
            ContentRating.valueOf(row.contentRating)
            MangaStatus.valueOf(row.status)
            ReadingMode.valueOf(row.readingMode)
        }
        val ids = metadata.map { it.id }.toSet()
        require(state.library.all { it.mangaId in ids })
        require(state.readingProgress.all { it.mangaId in ids })
        require(state.readingHistory.all { it.mangaId in ids })
    }
}

@Serializable
internal data class BackupMetadata(
    val id: String, val title: String, val sourceId: String,
    val altTitles: List<String> = emptyList(), val coverUrl: String? = null, val bannerUrl: String? = null,
    val author: String? = null, val artist: String? = null, val synopsis: String? = null,
    val score: Double? = null, val status: String = "UNKNOWN", val genres: List<String> = emptyList(),
    val contentRating: String = "SAFE", val readingMode: String = "MANGA_RTL", val popularity: Long? = null,
) {
    fun toEntity() = MangaEntity(id, title, altTitles, coverUrl, bannerUrl, author, artist, synopsis, score,
        MangaStatus.valueOf(status), genres, ContentRating.valueOf(contentRating), sourceId,
        ReadingMode.valueOf(readingMode), popularity)

    companion object {
        fun from(row: MangaEntity) = BackupMetadata(row.id, row.title, row.sourceId, row.altTitles,
            row.coverUrl, row.bannerUrl, row.author, row.artist, row.synopsis, row.score,
            row.status.name, row.genres, row.contentRating.name, row.readingMode.name, row.popularity)
    }
}
