package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionSource
import com.yomira.domain.extension.ExtensionLogo
import com.yomira.domain.extension.requireHttps
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.GZIPInputStream

/** Wire schema: https://github.com/keiyoushi/extensions-source/blob/main/.github/scripts/index.proto */
internal object ProtoRepositoryParser {
    private const val MAX_BYTES = 16 * 1024 * 1024

    fun decompress(bytes: ByteArray): ByteArray {
        if (bytes.size < 2 || bytes[0] != 0x1f.toByte() || bytes[1] != 0x8b.toByte()) return bytes
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "Repository index too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    fun parse(bytes: ByteArray, indexUrl: String): List<ExtensionDescriptor> {
        requireHttps(indexUrl)
        val index = Wire(bytes).fields()
        require(index.none { it.number == 102 }) { "External extension lists are not supported" }
        val list = index.singleOrNull { it.number == 101 }?.bytes()
            ?: throw IllegalArgumentException("Missing repository extension list")
        val entries = Wire(list).fields().filter { it.number == 1 }.map { extension(it.bytes(), indexUrl) }
        require(entries.map { it.packageName }.distinct().size == entries.size) { "Duplicate package names" }
        return entries
    }

    private fun extension(bytes: ByteArray, indexUrl: String): ExtensionDescriptor {
        val fields = Wire(bytes).fields()
        fun text(number: Int) = fields.lastOrNull { it.number == number }?.text().orEmpty()
        val resources = Wire(fields.last { it.number == 3 }.bytes()).fields()
        val apk = resources.last { it.number == 1 }.text()
        val apkUrl = URI(indexUrl).resolve(apk).toString()
        requireHttps(apkUrl)
        val sources = fields.filter { it.number == 8 }.map { field ->
            val source = Wire(field.bytes()).fields()
            fun sourceText(number: Int) = source.lastOrNull { it.number == number }?.text().orEmpty()
            ExtensionSource((source.lastOrNull { it.number == 1 }?.numberValue() ?: 0L).toString(), sourceText(2), sourceText(3))
        }
        val languages = sources.map { it.language }.distinct()
        // Extension.contentWarning (7), NOT a Source field. Upstream has no per-source
        // warning: MIXED means the catalog can contain adult titles, not that every title is adult.
        // Preserve the conservative catalog flag for safe-mode exclusion.
        val warning = fields.lastOrNull { it.number == 7 }?.numberValue() ?: 0
        return ExtensionDescriptor(
            packageName = text(2), name = text(1), language = languages.singleOrNull() ?: "all",
            nsfw = warning == 2L || warning == 3L, versionName = text(6),
            versionCode = fields.lastOrNull { it.number == 5 }?.numberValue() ?: 0,
            apkUrl = apkUrl, sources = sources,
            iconUrl = ExtensionLogo.resolveIcon(resources.lastOrNull { it.number == 2 }?.text(), indexUrl),
            repositoryUrl = indexUrl,
        )
    }

    private data class Field(val number: Int, val wire: Int, val data: ByteArray?, val value: Long) {
        fun bytes(): ByteArray { require(wire == 2); return requireNotNull(data) }
        fun text(): String = bytes().toString(Charsets.UTF_8)
        fun numberValue(): Long { require(wire == 0); return value }
    }

    private class Wire(private val data: ByteArray) {
        private var pos = 0
        private fun varint(): Long {
            var result = 0L
            for (i in 0..9) {
                require(pos < data.size) { "Truncated protobuf varint" }
                val b = data[pos++].toInt() and 255
                require(i != 9 || b <= 1) { "Invalid protobuf varint" }
                result = result or ((b and 127).toLong() shl (i * 7))
                if (b and 128 == 0) return result
            }
            throw IllegalArgumentException("Invalid protobuf varint")
        }
        fun fields(): List<Field> {
            val fields = mutableListOf<Field>()
            while (pos < data.size) {
                val tag = varint()
                val number = (tag ushr 3).toInt()
                val wire = (tag and 7).toInt()
                require(number > 0 && tag ushr 3 <= 0x1fffffff) { "Invalid protobuf field" }
                when (wire) {
                    0 -> fields += Field(number, wire, null, varint())
                    2 -> {
                        val length = varint()
                        require(length >= 0 && length <= data.size - pos) { "Invalid protobuf length" }
                        val end = pos + length.toInt()
                        fields += Field(number, wire, data.copyOfRange(pos, end), 0)
                        pos = end
                    }
                    1, 5 -> {
                        val size = if (wire == 1) 8 else 4
                        require(size <= data.size - pos) { "Truncated protobuf field" }
                        pos += size
                    }
                    else -> throw IllegalArgumentException("Unsupported protobuf wire type")
                }
            }
            return fields
        }
    }
}
