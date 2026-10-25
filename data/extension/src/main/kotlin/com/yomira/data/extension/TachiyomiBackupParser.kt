package com.yomira.data.extension

import com.yomira.domain.extension.BackupFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Decodes Tachiyomi / Mihon / fork library backups into [TachiyomiBackupDocument].
 *
 * The reader is dependency-free on purpose: a ~120 line protobuf wire reader covers the
 * documented `Backup` schema, so Yomira can ingest `.tachibk` (gzip or raw protobuf) and
 * legacy JSON backups without dragging a protobuf runtime into a reader app.
 *
 * Field numbers implemented here mirror the upstream models
 * (`eu.kanade.tachiyomi.data.backup.models.*` in Mihon and its forks):
 * `Backup(backupManga=1, backupCategories=2)`,
 * `BackupManga(source=1, url=2, title=3, artist=4, author=5, description=6, genre=7,
 * status=8, thumbnailUrl=9, dateAdded=13, chapters=16, categories=17, favorite=100)`,
 * `BackupChapter(url=1, name=2, scanlator=3, read=4, bookmark=5, lastPageRead=6, dateFetch=7,
 * dateUpload=8, chapterNumber=9)`, `BackupCategory(name=1, order=2, id=3)`.
 *
 * Anything else on the wire (tracking, history, preferences, fork extras) is skipped
 * byte-exactly, so newer backup versions import the library instead of exploding.
 */
class TachiyomiBackupParser(
    private val maxCompressedBytes: Long = DEFAULT_MAX_COMPRESSED_BYTES,
    private val maxPlainBytes: Long = DEFAULT_MAX_PLAIN_BYTES,
) {
    init {
        require(maxCompressedBytes > 0 && maxPlainBytes >= maxCompressedBytes) { "Invalid backup size limits" }
    }

    fun parse(bytes: ByteArray): TachiyomiBackupDocument {
        require(bytes.isNotEmpty()) { "Backup file is empty" }
        require(bytes.size.toLong() <= maxCompressedBytes) { "Backup file is larger than $maxCompressedBytes bytes" }
        val payload = if (bytes.isGzip()) inflate(bytes) else bytes
        require(payload.size.toLong() <= maxPlainBytes) { "Decompressed backup exceeds $maxPlainBytes bytes" }
        val firstSignificant = payload.firstOrNull { !it.isJsonWhitespace() }
            ?: throw IllegalArgumentException("Backup file is empty after decompression")
        return if (firstSignificant == BYTE_BRACE || firstSignificant == BYTE_BRACKET) {
            parseJson(String(payload, Charsets.UTF_8))
        } else {
            parseProtobuf(payload)
        }
    }

    // ---------------------------------------------------------------- protobuf path

    internal fun parseProtobuf(bytes: ByteArray): TachiyomiBackupDocument {
        val reader = ProtoReader(bytes, 0, bytes.size)
        val mangas = mutableListOf<BackupMangaRecord>()
        val categories = mutableListOf<BackupCategoryRecord>()
        while (!reader.exhausted()) {
            val (number, wireType) = reader.readTag()
            when {
                number == 1 && wireType == WIRE_LENGTH_DELIMITED -> mangas += readManga(reader.readLengthDelimited())
                number == 2 && wireType == WIRE_LENGTH_DELIMITED -> categories += readCategory(reader.readLengthDelimited())
                else -> reader.skip(wireType)
            }
        }
        return TachiyomiBackupDocument(BackupFormat.PROTOBUF, categories.toList(), mangas.toList())
    }

    private fun readManga(bytes: ByteArray): BackupMangaRecord {
        val reader = ProtoReader(bytes, 0, bytes.size)
        var sourceId = 0L
        var url = ""
        var title = ""
        var artist: String? = null
        var author: String? = null
        var description: String? = null
        var status = 0
        var thumbnailUrl: String? = null
        var dateAdded = 0L
        var favorite = true
        val genres = mutableListOf<String>()
        val categoryIds = mutableListOf<Long>()
        val chapters = mutableListOf<BackupChapterRecord>()
        while (!reader.exhausted()) {
            val (number, wireType) = reader.readTag()
            when {
                number == 1 && wireType == WIRE_VARINT -> sourceId = reader.readVarint()
                number == 2 && wireType == WIRE_LENGTH_DELIMITED -> url = reader.readString()
                number == 3 && wireType == WIRE_LENGTH_DELIMITED -> title = reader.readString()
                number == 4 && wireType == WIRE_LENGTH_DELIMITED -> artist = reader.readString()
                number == 5 && wireType == WIRE_LENGTH_DELIMITED -> author = reader.readString()
                number == 6 && wireType == WIRE_LENGTH_DELIMITED -> description = reader.readString()
                number == 7 && wireType == WIRE_LENGTH_DELIMITED -> genres += reader.readString()
                number == 8 && wireType == WIRE_VARINT -> status = reader.readVarint().toInt()
                number == 9 && wireType == WIRE_LENGTH_DELIMITED -> thumbnailUrl = reader.readString()
                number == 13 && wireType == WIRE_VARINT -> dateAdded = reader.readVarint()
                number == 16 && wireType == WIRE_LENGTH_DELIMITED -> chapters += readChapter(reader.readLengthDelimited())
                number == 17 && wireType == WIRE_LENGTH_DELIMITED -> {
                    // Packed repeated category ids (protobuf default for repeated varints).
                    val packed = ProtoReader(reader.readLengthDelimited(), 0, -1)
                    while (!packed.exhausted()) categoryIds += packed.readVarint()
                }
                number == 17 && wireType == WIRE_VARINT -> categoryIds += reader.readVarint()
                number == 100 && wireType == WIRE_VARINT -> favorite = reader.readVarint() != 0L
                else -> reader.skip(wireType)
            }
        }
        return BackupMangaRecord(
            rowId = null,
            sourceId = sourceId,
            url = url.trim(),
            title = title.trim(),
            artist = artist?.trim()?.takeIf { it.isNotEmpty() },
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            genres = genres.toList(),
            thumbnailUrl = thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() },
            status = status,
            favorite = favorite,
            dateAdded = dateAdded.coerceAtLeast(0L),
            categoryIds = categoryIds.toList(),
            chapters = chapters.toList(),
        )
    }

    private fun readChapter(bytes: ByteArray): BackupChapterRecord {
        val reader = ProtoReader(bytes, 0, bytes.size)
        var url = ""
        var name = ""
        var scanlator: String? = null
        var read = false
        var bookmark = false
        var lastPageRead = 0L
        var dateFetch = 0L
        var dateUpload = 0L
        var chapterNumber = 0.0
        while (!reader.exhausted()) {
            val (number, wireType) = reader.readTag()
            when {
                number == 1 && wireType == WIRE_LENGTH_DELIMITED -> url = reader.readString()
                number == 2 && wireType == WIRE_LENGTH_DELIMITED -> name = reader.readString()
                number == 3 && wireType == WIRE_LENGTH_DELIMITED -> scanlator = reader.readString()
                number == 4 && wireType == WIRE_VARINT -> read = reader.readVarint() != 0L
                number == 5 && wireType == WIRE_VARINT -> bookmark = reader.readVarint() != 0L
                number == 6 && wireType == WIRE_VARINT -> lastPageRead = reader.readVarint()
                number == 7 && wireType == WIRE_VARINT -> dateFetch = reader.readVarint()
                number == 8 && wireType == WIRE_VARINT -> dateUpload = reader.readVarint()
                // Upstream stores the number as a float (fixed32); tolerate other encodings.
                number == 9 && wireType == WIRE_FIXED32 -> chapterNumber = java.lang.Float.intBitsToFloat(reader.readFixed32()).toDouble()
                number == 9 && wireType == WIRE_FIXED64 -> chapterNumber = java.lang.Double.longBitsToDouble(reader.readFixed64())
                number == 9 && wireType == WIRE_VARINT -> chapterNumber = reader.readVarint().toDouble()
                else -> reader.skip(wireType)
            }
        }
        return BackupChapterRecord(
            mangaRowId = null,
            url = url.trim(),
            name = name.trim(),
            scanlator = scanlator?.trim()?.takeIf { it.isNotEmpty() },
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            chapterNumber = chapterNumber.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            dateFetch = dateFetch.coerceAtLeast(0L),
            dateUpload = dateUpload.coerceAtLeast(0L),
        )
    }

    private fun readCategory(bytes: ByteArray): BackupCategoryRecord {
        val reader = ProtoReader(bytes, 0, bytes.size)
        var name = ""
        var order = 0L
        var id: Long? = null
        while (!reader.exhausted()) {
            val (number, wireType) = reader.readTag()
            when {
                number == 1 && wireType == WIRE_LENGTH_DELIMITED -> name = reader.readString()
                number == 2 && wireType == WIRE_VARINT -> order = reader.readVarint()
                number == 3 && wireType == WIRE_VARINT -> id = reader.readVarint()
                else -> reader.skip(wireType)
            }
        }
        return BackupCategoryRecord(id, name.trim(), order)
    }

    // -------------------------------------------------------------------- JSON path

    internal fun parseJson(text: String): TachiyomiBackupDocument {
        val root = try {
            lenientJson.parseToJsonElement(text)
        } catch (error: Exception) {
            throw IllegalArgumentException("Backup is neither a valid protobuf nor JSON document", error)
        }
        val body = when (root) {
            is JsonObject -> root
            is JsonArray -> root.singleOrNull()?.jsonObject
                ?: throw IllegalArgumentException("Backup JSON array must contain exactly one backup object")
            else -> throw IllegalArgumentException("Backup JSON must be an object")
        }
        val categories = body.arrayOf("categories").map { element ->
            val category = element.jsonObject
            BackupCategoryRecord(
                id = category.longOf("id"),
                name = category.stringOf("name").orEmpty().trim(),
                order = category.longOf("order") ?: 0L,
            )
        }
        val categoryAlias = buildMap {
            val map = body.objectOf("categoriesMap") ?: body.objectOf("categories_map") ?: return@buildMap
            map.forEach { (key, value) ->
                val from = key.toLongOrNull() ?: return@forEach
                val to = (value as? JsonPrimitive)?.longOrNull
                    ?: (value as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                if (to != null) put(from, to)
            }
        }
        val mangas = body.arrayOf("mangas").map { element -> readJsonManga(element.jsonObject) }
        val standalone = body.arrayOf("chapters").map { element -> readJsonChapter(element.jsonObject) }
        return TachiyomiBackupDocument(
            format = BackupFormat.JSON,
            categories = categories,
            mangas = mangas,
            standaloneChapters = standalone,
            categoryAlias = categoryAlias,
        )
    }

    private fun readJsonManga(entry: JsonObject): BackupMangaRecord = BackupMangaRecord(
        rowId = entry.longOf("id"),
        sourceId = entry.longOf("source") ?: 0L,
        url = entry.stringOf("url", "key").orEmpty().trim(),
        title = entry.stringOf("title", "custom_title").orEmpty().trim(),
        artist = entry.stringOf("artist", "custom_artist")?.trim()?.takeIf { it.isNotEmpty() },
        author = entry.stringOf("author", "custom_author")?.trim()?.takeIf { it.isNotEmpty() },
        description = entry.stringOf("description", "custom_description")?.trim()?.takeIf { it.isNotEmpty() },
        genres = entry.listOfStrings("genre", "genres"),
        thumbnailUrl = entry.stringOf("thumbnail_url", "thumbnailUrl", "cover")?.trim()?.takeIf { it.isNotEmpty() },
        status = entry.longOf("status")?.toInt() ?: 0,
        favorite = entry.boolOf("favorite", "favourite", "in_library") ?: true,
        dateAdded = (entry.longOf("date_added", "dateAdded") ?: 0L).coerceAtLeast(0L),
        categoryIds = entry.arrayOf("categories").mapNotNull { element ->
            (element as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }
        },
        chapters = entry.arrayOf("chapters").map { element -> readJsonChapter(element.jsonObject) },
    )

    private fun readJsonChapter(entry: JsonObject): BackupChapterRecord = BackupChapterRecord(
        mangaRowId = entry.longOf("manga", "manga_id", "mangaId"),
        url = entry.stringOf("url", "key").orEmpty().trim(),
        name = entry.stringOf("name", "title").orEmpty().trim(),
        scanlator = entry.stringOf("scanlator", "scanlators")?.trim()?.takeIf { it.isNotEmpty() },
        read = entry.boolOf("read") ?: false,
        bookmark = entry.boolOf("bookmark", "bookmarked") ?: false,
        lastPageRead = (entry.longOf("last_page_read", "lastPageRead", "progress") ?: 0L)
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        chapterNumber = entry.doubleOf("chapter_number", "chapterNumber", "number") ?: 0.0,
        dateFetch = (entry.longOf("date_fetch", "dateFetch") ?: 0L).coerceAtLeast(0L),
        dateUpload = (entry.longOf("date_upload", "dateUpload") ?: 0L).coerceAtLeast(0L),
    )

    // -------------------------------------------------------------------- container

    private companion object {
        val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
        const val DEFAULT_MAX_COMPRESSED_BYTES = 64L * 1024 * 1024
        const val DEFAULT_MAX_PLAIN_BYTES = 512L * 1024 * 1024

        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        const val BYTE_BRACE = '{'.code.toByte()
        const val BYTE_BRACKET = '['.code.toByte()

        fun inflate(bytes: ByteArray): ByteArray {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
                val output = ByteArrayOutputStream(minOf(bytes.size * 8, 16 * 1024 * 1024))
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = gzip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= DEFAULT_MAX_PLAIN_BYTES_LIMIT) { "Gzip bomb detected" }
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        }

        // Kept as a separate constant so the shared companion stays readable; equal to the
        // hard ceiling any decompressed backup may ever occupy.
        const val DEFAULT_MAX_PLAIN_BYTES_LIMIT = 1024L * 1024 * 1024
    }
}

/**
 * Minimal protobuf wire-format reader over one byte range. Supports the four wire types a
 * backup can contain; groups (3/4) are rejected like upstream parsers do.
 */
internal class ProtoReader(
    private val bytes: ByteArray,
    from: Int,
    until: Int,
) {
    private var position = from
    private val limit = if (until < 0) bytes.size else until

    fun exhausted(): Boolean = position >= limit

    /** @return `fieldNumber to wireType` for the next tag. */
    fun readTag(): Pair<Int, Int> {
        val tag = readVarint()
        val number = tag ushr 3
        val wireType = (tag and 0x7L).toInt()
        require(number in 1 until (1L shl 29)) { "Malformed backup: field number $number" }
        require(wireType != 3 && wireType != 4) { "Protobuf groups are not supported by backup files" }
        return number.toInt() to wireType
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = readByte().toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            require(shift <= 64) { "Malformed varint" }
        }
    }

    fun readFixed32(): Int {
        require(position + 4 <= limit) { "Truncated fixed32" }
        val value = bytes[position].toInt() and 0xFF or
            ((bytes[position + 1].toInt() and 0xFF) shl 8) or
            ((bytes[position + 2].toInt() and 0xFF) shl 16) or
            ((bytes[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    fun readFixed64(): Long {
        require(position + 8 <= limit) { "Truncated fixed64" }
        var value = 0L
        for (index in 0 until 8) value = value or ((bytes[position + index].toLong() and 0xFF) shl (8 * index))
        position += 8
        return value
    }

    fun readLengthDelimited(): ByteArray {
        val length = readVarint()
        require(length in 0L..(limit - position).toLong()) { "Length $length exceeds the remaining input" }
        val slice = bytes.copyOfRange(position, position + length.toInt())
        position += length.toInt()
        return slice
    }

    fun readString(): String = String(readLengthDelimited(), Charsets.UTF_8)

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> require(position + 8 <= limit).also { position += 8 }
            2 -> readLengthDelimited()
            5 -> require(position + 4 <= limit).also { position += 4 }
            else -> throw IllegalArgumentException("Unsupported wire type $wireType")
        }
    }

    private fun readByte(): Byte {
        require(position < limit) { "Truncated protobuf input" }
        return bytes[position++]
    }
}

// --- small JSON helpers shared by the parser (kept here to keep the file self-contained) ---

private fun JsonObject.arrayOf(key: String): List<JsonElement> =
    (this[key] as? JsonArray)?.toList().orEmpty()

private fun ByteArray.isGzip(): Boolean =
    size >= 2 && this[0] == 0x1F.toByte() && this[1] == 0x8B.toByte()

private fun Byte.isJsonWhitespace(): Boolean = this == ' '.code.toByte() || this == '\t'.code.toByte() ||
    this == '\r'.code.toByte() || this == '\n'.code.toByte()

private fun JsonObject.objectOf(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonElement?.stringContentOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonObject.stringOf(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key].stringContentOrNull()
        if (value != null) return value
    }
    return null
}

private fun JsonObject.longOf(vararg keys: String): Long? {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        primitive.longOrNull?.let { return it }
        primitive.contentOrNull?.let { text ->
            text.toLongOrNull()?.let { return it }
            text.toDoubleOrNull()?.let { if (it.isFinite()) return it.toLong() }
        }
    }
    return null
}

private fun JsonObject.doubleOf(vararg keys: String): Double? {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        primitive.doubleOrNull?.let { if (it.isFinite() && it >= 0.0) return it }
        primitive.floatOrNull?.let { if (it.isFinite() && it >= 0f) return it.toDouble() }
        primitive.contentOrNull?.toDoubleOrNull()?.let { if (it.isFinite() && it >= 0.0) return it }
    }
    return null
}

private fun JsonObject.boolOf(vararg keys: String): Boolean? {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        primitive.booleanOrNull?.let { return it }
        primitive.contentOrNull?.let { text ->
            if (text == "1") return true
            if (text == "0") return false
            text.toBooleanStrictOrNull()?.let { return it }
        }
    }
    return null
}

private fun JsonObject.listOfStrings(vararg keys: String): List<String> {
    for (key in keys) {
        val element = this[key] ?: continue
        when (element) {
            is JsonArray -> return element.mapNotNull { it.stringContentOrNull() }
                .map { it.trim() }.filter { it.isNotEmpty() }
            is JsonPrimitive -> return element.contentOrNull?.split(',')
                ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            else -> Unit
        }
    }
    return emptyList()
}
