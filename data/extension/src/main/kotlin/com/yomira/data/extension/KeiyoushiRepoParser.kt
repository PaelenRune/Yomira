package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionSource
import com.yomira.domain.extension.requireHttps
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OutdatedRepositoryClientException : IllegalArgumentException("Repository does not recognize this client version")

class KeiyoushiRepoParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(response: RepositoryResponse, indexUrl: String): List<ExtensionDescriptor> {
        val bytes = ProtoRepositoryParser.decompress(response.bytes)
        val first = bytes.firstOrNull { it.toInt().toChar() !in " \t\r\n" }
        val explicitlyProto = java.net.URI(indexUrl).path.endsWith(".pb", ignoreCase = true) ||
            response.contentType?.contains("protobuf", ignoreCase = true) == true
        val jsonPayload = !explicitlyProto && (first == '{'.code.toByte() || first == '['.code.toByte() ||
            bytes.take(13).toByteArray().toString(Charsets.UTF_8).startsWith("YOMIRA-XOR-1:"))
        return if (jsonPayload) parse(bytes.toString(Charsets.UTF_8), indexUrl)
        else ProtoRepositoryParser.parse(bytes, indexUrl)
    }

    fun parse(payload: String, indexUrl: String): List<ExtensionDescriptor> {
        requireHttps(indexUrl)
        val entries = json.parseToJsonElement(RepositoryPayload.decode(payload)) as? JsonArray
            ?: throw IllegalArgumentException("Repository index must be a JSON array")
        if (entries.size in 1..2 && entries.any {
                (it as? JsonObject)?.text("name")?.equals("Outdated App", ignoreCase = true) == true
            }) throw OutdatedRepositoryClientException()
        val extensions = entries.mapIndexed { index, element ->
            try {
                val entry = element.jsonObject
                val apk = entry.text("apkUrl") ?: entry.required("apk")
                val url = if (URI(apk).isAbsolute) apk else {
                    require(!apk.startsWith("/") && !apk.contains("..") && !apk.contains('\\')) { "Unsafe APK path" }
                    URI(indexUrl).resolve("apk/$apk").toString()
                }
                requireHttps(url)
                val language = entry.required("lang")
                val sources = (entry["sources"] as? JsonArray).orEmpty().map { item ->
                    val source = item.jsonObject
                    ExtensionSource(source.required("id"), source.required("name"), source.text("lang") ?: language)
                }
                require(sources.map { it.id }.distinct().size == sources.size) { "Duplicate source IDs" }
                val nsfw = when (entry["nsfw"]?.jsonPrimitive?.content) {
                    "0", "false" -> false
                    "1", "true" -> true
                    else -> throw IllegalArgumentException("Invalid nsfw value")
                }
                ExtensionDescriptor(
                    packageName = entry.required("pkgName"),
                    name = entry.required("name"),
                    language = language,
                    nsfw = nsfw,
                    versionName = entry.text("version") ?: entry.required("versionName"),
                    versionCode = entry.required("code").toLong(),
                    apkUrl = url,
                    sources = sources,
                )
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid repository entry at index $index", error)
            }
        }
        require(extensions.map { it.packageName }.distinct().size == extensions.size) { "Duplicate package names" }
        return extensions
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.required(key: String): String = text(key)?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing $key")
}
