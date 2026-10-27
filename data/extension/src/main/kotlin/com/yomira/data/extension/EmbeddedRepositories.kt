package com.yomira.data.extension

import com.yomira.domain.extension.requireHttps
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
    val group: String? = null,
    val westernPackages: List<String> = emptyList(),
)

/** JVM resources are carried into the Android APK too; an empty URL store needs no setup. */
object EmbeddedRepositories {
    @Serializable
    private data class CatalogEntry(
        val resource: String,
        val indexUrl: String,
        val upstreamUrl: String? = null,
        val group: String? = null,
        val westernPackages: List<String> = emptyList(),
    )

    fun load(): List<EmbeddedRepository> {
        val entries = Json.decodeFromString(ListSerializer(CatalogEntry.serializer()), resource("catalog.json"))
        require(entries.map { it.indexUrl }.distinct().size == entries.size) { "Duplicate embedded repositories" }
        return entries.map {
            requireHttps(it.indexUrl)
            require(it.resource.matches(Regex("[a-zA-Z0-9_/.\\-]+\\.(json|enc)")) && !it.resource.contains(".."))
            it.upstreamUrl?.let(::requireHttps)
            EmbeddedRepository(it.indexUrl, RepositoryPayload.decode(resource(it.resource), it.resource.endsWith(".enc")),
                it.upstreamUrl, it.group, it.westernPackages)
        }
    }

    /** Upstream v2 metadata is converted in memory; executable APKs are never downloaded here. */
    internal fun refreshedPayload(repository: EmbeddedRepository, upstream: String): String {
        val extensions = Json.parseToJsonElement(upstream).jsonObject
            .getValue("extensionList").jsonObject.getValue("extensions").jsonArray
        require(extensions.size >= 100) { "Upstream index looks truncated" }
        val entries = extensions.filter { element ->
            val pkg = element.jsonObject.getValue("packageName").jsonPrimitive.content
            val turkish = pkg.startsWith("eu.kanade.tachiyomi.extension.tr.")
            val western = pkg.substringAfterLast('.') in repository.westernPackages
            when (repository.group) {
                "turkish" -> turkish
                "western" -> !turkish && western
                "global" -> !turkish && !western
                else -> error("Unknown embedded repository group")
            }
        }.map { element ->
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
        }
        return JsonArray(entries).toString()
    }

    private fun resource(name: String): String =
        requireNotNull(EmbeddedRepositories::class.java.getResourceAsStream("/yomira/repositories/$name")) {
            "Missing embedded repository resource: $name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
