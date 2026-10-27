package com.yomira.data.extension

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Inspected data, not executable scraping rules. Compiled APK behavior stays in pinned DEX. */
@Serializable
data class ApkMetadata(
    val packageName: String? = null,
    val versionCode: Long? = null,
    val entryClasses: List<String> = emptyList(),
    val assets: List<ApkAsset> = emptyList(),
    val manifestSha256: String? = null,
)

@Serializable
data class ApkAsset(val name: String, val sizeBytes: Long, val sha256: String)

/** Parses text or Android binary XML without executing classes or registering an Android package. */
internal object ApkMetadataInspector {
    fun manifest(bytes: ByteArray): ApkMetadata {
        val attributes = if (bytes.firstOrNull()?.toInt() == 3) binaryXml(bytes) else textXml(bytes)
        val root = attributes.firstOrNull { it.first == "manifest" }?.second
            ?: throw IllegalArgumentException("APK manifest has no manifest element")
        val packageName = requireNotNull(root["package"]) { "APK manifest has no package" }
        val classes = attributes.filter { it.first == "meta-data" }.mapNotNull { (_, values) ->
            values["value"]?.takeIf { values["name"] == "tachiyomi.extension.class" }
        }.flatMap { it.split(';') }.filter { it.isNotBlank() }.map {
            when {
                it.startsWith('.') -> packageName + it
                '.' !in it -> "$packageName.$it"
                else -> it
            }
        }
        return ApkMetadata(packageName, root["versionCode"]?.toLong(), classes.distinct().sorted(),
            manifestSha256 = ApkSandboxExtractor.sha256Hex(bytes))
    }

    /** Optional native metadata asset can declare exact entry classes; publisher identity cannot change. */
    fun assetClasses(bytes: ByteArray, packageName: String): List<String> {
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        root["packageName"]?.jsonPrimitive?.contentOrNull?.let {
            require(it == packageName) { "APK JSON asset package mismatch" }
        }
        return (root["entryClasses"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.also {
            require(it.size <= 128 && it.all { name -> name.matches(Regex("[A-Za-z_$][A-Za-z0-9_.$/]*")) }) {
                "Invalid APK JSON entry classes"
            }
        }
    }

    private fun textXml(bytes: ByteArray): List<Pair<String, Map<String, String>>> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val nodes = document.getElementsByTagName("*")
        return (0 until nodes.length).map { index ->
            val node = nodes.item(index)
            node.nodeName to (0 until node.attributes.length).associate { attributeIndex ->
                val attribute = node.attributes.item(attributeIndex)
                (attribute.localName ?: attribute.nodeName.substringAfter(':')) to attribute.nodeValue
            }
        }
    }

    private fun binaryXml(bytes: ByteArray): List<Pair<String, Map<String, String>>> {
        fun u16(offset: Int): Int {
            require(offset >= 0 && offset.toLong() + 2 <= bytes.size) { "Truncated binary manifest" }
            return (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
        }
        fun i32(offset: Int): Int = u16(offset) or (u16(offset + 2) shl 16)
        require(u16(0) == 3 && u16(2) == 8 && i32(4) == bytes.size) { "Invalid binary XML header" }
        var strings = emptyList<String>()
        val result = mutableListOf<Pair<String, Map<String, String>>>()
        var cursor = 8
        while (cursor < bytes.size) {
            val type = u16(cursor)
            val header = u16(cursor + 2)
            val size = i32(cursor + 4)
            require(header >= 8 && size >= header && cursor.toLong() + size <= bytes.size) { "Invalid XML chunk" }
            val end = cursor + size
            fun string(index: Int): String {
                require(index in strings.indices) { "Invalid manifest string index" }
                return strings[index]
            }
            if (type == 1) {
                require(header >= 28) { "Invalid string pool" }
                val count = i32(cursor + 8)
                val utf8 = i32(cursor + 16) and 0x100 != 0
                val start = i32(cursor + 20)
                require(count in 0..65536 && header.toLong() + count * 4L <= size && start in header..size)
                strings = (0 until count).map { index ->
                    val relative = i32(cursor + header + index * 4)
                    require(relative >= 0 && start.toLong() + relative < size)
                    var position = cursor + start + relative
                    fun length(): Int {
                        require(position + (if (utf8) 1 else 2) <= end)
                        val first = if (utf8) bytes[position++].toInt() and 255 else u16(position).also { position += 2 }
                        val mask = if (utf8) 0x80 else 0x8000
                        return if (first and mask == 0) first else {
                            require(position + (if (utf8) 1 else 2) <= end)
                            val second = if (utf8) bytes[position++].toInt() and 255 else u16(position).also { position += 2 }
                            ((first and (mask - 1)) shl (if (utf8) 8 else 16)) or second
                        }
                    }
                    if (utf8) length() // UTF-16 length precedes the encoded byte length.
                    val length = length().toLong() * if (utf8) 1 else 2
                    require(length in 0..1_048_576 && position.toLong() + length + (if (utf8) 1 else 2) <= end)
                    String(bytes, position, length.toInt(), if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE)
                }
            } else if (type == 0x102) {
                require(header >= 16 && size >= 36)
                val extension = cursor + header
                val name = string(i32(extension + 4))
                val attributeStart = u16(extension + 8)
                val attributeSize = u16(extension + 10)
                val count = u16(extension + 12)
                require(attributeStart >= 20 && attributeSize >= 20 &&
                    header.toLong() + attributeStart + count.toLong() * attributeSize <= size)
                val values = (0 until count).associate { index ->
                    val attribute = extension + attributeStart + index * attributeSize
                    val raw = i32(attribute + 8)
                    val dataType = bytes[attribute + 15].toInt() and 255
                    val data = i32(attribute + 16)
                    val value = when {
                        raw != -1 -> string(raw)
                        dataType == 3 -> string(data)
                        dataType in 0x10..0x1f -> (data.toLong() and 0xffffffffL).toString()
                        else -> ""
                    }
                    string(i32(attribute + 4)) to value
                }
                result += name to values
            }
            cursor = end
        }
        return result
    }
}
