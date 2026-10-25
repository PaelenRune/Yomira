package com.yomira.data.extension

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Test-only builders: fabricated DEX payloads, fake repository APKs and protobuf bytes. */
object BackupTestFixtures {

    /**
     * Produces the smallest byte sequence a real Android toolchain would call a DEX file:
     * a valid 112-byte header (magic, version, endian tag, file size) followed by the
     * `string_ids` table with the given strings encoded as `uleb128 length + ASCII`.
     */
    fun dex(version: String = "039", strings: List<String>): ByteArray {
        val headerSize = 0x70
        val stringsSizeOffset = headerSize
        val dataStart = headerSize + strings.size * 4
        val data = ByteArrayOutputStream()
        val offsets = strings.map { text ->
            val offset = dataStart + data.size()
            data.write(uleb128(text.toByteArray(Charsets.US_ASCII).size))
            data.write(text.toByteArray(Charsets.US_ASCII))
            offset
        }
        val total = dataStart + data.size()

        val buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("dex\n".toByteArray(Charsets.US_ASCII))
        buffer.put(version.toByteArray(Charsets.US_ASCII))
        buffer.put(0) // magic terminator
        buffer.putInt(0) // checksum — not validated by the inspector
        repeat(20) { buffer.put(0) } // signature placeholder
        buffer.putInt(total) // file_size
        buffer.putInt(headerSize) // header_size
        buffer.putInt(0x12345678) // endian tag
        buffer.putInt(0) // link_size
        buffer.putInt(0) // link_off
        buffer.putInt(0) // map_off
        buffer.putInt(strings.size) // string_ids_size
        buffer.putInt(headerSize) // string_ids_off
        repeat((headerSize - buffer.position()) / 4) { buffer.putInt(0) } // remaining header fields
        offsets.forEach { buffer.putInt(it) }
        buffer.put(data.toByteArray())
        return buffer.array()
    }

    fun dexWithEntryClass(packageName: String): ByteArray = dex(
        strings = listOf(
            "Lkotlin/Metadata;",
            "generated-by/yomira-fixture",
            "${packageName.replace('.', '/')}/${packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }}Extension",
            "${packageName.replace('.', '/')}/${packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }}Source",
        ),
    )

    /** A fake repository APK: a zip with dex payloads plus files the extractor must ignore. */
    fun apk(dexEntries: Map<String, ByteArray>, extras: Map<String, ByteArray> = emptyMap()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (dexEntries + extras).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun apkWithSingleDex(packageName: String): ByteArray = apk(
        dexEntries = mapOf("classes.dex" to dexWithEntryClass(packageName)),
        extras = mapOf(
            "AndroidManifest.xml" to byteArrayOf(0x03, 0x00, 0x08, 0x00), // binary AXML is ignored
            "META-INF/CERT.SF" to "signed-by-someone-else".toByteArray(),
        ),
    )

    fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun uleb128(value: Int): ByteArray {
        var remaining = value
        val out = ByteArrayOutputStream()
        while (true) {
            var byte = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) byte = byte or 0x80
            out.write(byte)
            if (remaining == 0) return out.toByteArray()
        }
    }
}

/**
 * Hand-rolled protobuf writer mirroring the upstream `Backup*` message shapes so parser tests
 * exercise the exact wire format Mihon emits (including packed repeated fields and the
 * float-encoded chapter number).
 */
class ProtoTestWriter {
    private val out = ByteArrayOutputStream()

    fun varintField(number: Int, value: Long): ProtoTestWriter {
        tag(number, 0)
        writeVarint(value)
        return this
    }

    fun boolField(number: Int, value: Boolean): ProtoTestWriter = varintField(number, if (value) 1 else 0)

    fun stringField(number: Int, value: String): ProtoTestWriter = bytesField(number, value.toByteArray(Charsets.UTF_8))

    fun bytesField(number: Int, value: ByteArray): ProtoTestWriter {
        tag(number, 2)
        writeVarint(value.size.toLong())
        out.write(value)
        return this
    }

    fun messageField(number: Int, message: ProtoTestWriter): ProtoTestWriter = bytesField(number, message.toByteArray())

    fun packedLongs(number: Int, values: List<Long>): ProtoTestWriter {
        val inner = ProtoTestWriter()
        values.forEach { inner.writeVarint(it) }
        return bytesField(number, inner.toByteArray())
    }

    fun floatField(number: Int, value: Float): ProtoTestWriter {
        tag(number, 5)
        val bits = java.lang.Float.floatToIntBits(value)
        for (index in 0 until 4) out.write((bits ushr (8 * index)) and 0xFF)
        return this
    }

    fun doubleField(number: Int, value: Double): ProtoTestWriter {
        tag(number, 1)
        val bits = java.lang.Double.doubleToLongBits(value)
        for (index in 0 until 8) out.write(((bits ushr (8 * index)) and 0xFFL).toInt())
        return this
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(number: Int, wireType: Int) = writeVarint(((number.toLong() shl 3) or wireType.toLong()))

    private fun writeVarint(value: Long) {
        var remaining = value
        while (true) {
            var byte = (remaining and 0x7F).toInt()
            remaining = remaining ushr 7
            if (remaining != 0L) byte = byte or 0x80
            out.write(byte)
            if (remaining == 0L) return
        }
    }
}
