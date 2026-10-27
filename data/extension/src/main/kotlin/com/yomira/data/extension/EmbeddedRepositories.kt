package com.yomira.data.extension

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class EmbeddedRepository(
    val indexUrl: String,
    val payload: String,
    val upstreamUrl: String? = null,
)

/** Clean JVM resources are also packaged in the Android APK; no URL setup is required. */
object EmbeddedRepositories {
    const val UPSTREAM_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json"

    fun load(): List<EmbeddedRepository> {
        val payload = requireNotNull(javaClass.getResourceAsStream("/assets/keiyoushi/index.min.json")) {
            "Missing bundled Keiyoushi manifest"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return listOf(EmbeddedRepository(UPSTREAM_URL, payload, UPSTREAM_URL))
    }

    /** Convert all upstream v2 metadata, without selecting or favoring any site. */
    internal fun refreshedPayload(upstream: String): String {
        val extensions = Json.parseToJsonElement(upstream).jsonObject
            .getValue("extensionList").jsonObject.getValue("extensions").jsonArray
        require(extensions.size >= 100) { "Upstream index looks truncated" }
        return JsonArray(extensions.map { element ->
            val entry = element.jsonObject
            val sources = entry.getValue("sources").jsonArray
            buildJsonObject {
                put("name", entry.getValue("name"))
                put("pkgName", entry.getValue("packageName"))
                put("apkUrl", entry.getValue("resources").jsonObject.getValue("apkUrl"))
                put("lang", sources.firstOrNull()?.jsonObject?.get("language") ?: JsonPrimitive("all"))
                put("nsfw", if (entry["contentWarning"]?.jsonPrimitive?.content == "CONTENT_WARNING_NSFW") 1 else 0)
                put("version", entry.getValue("versionName"))
                put("code", entry.getValue("versionCode"))
                put("sources", JsonArray(sources.map { source ->
                    val value = source.jsonObject
                    JsonObject(mapOf("id" to value.getValue("id"), "name" to value.getValue("name"),
                        "lang" to value.getValue("language")))
                }))
            }
        }).toString()
    }
}
