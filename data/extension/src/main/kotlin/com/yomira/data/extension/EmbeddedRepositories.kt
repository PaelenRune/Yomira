package com.yomira.data.extension

import com.yomira.domain.extension.requireHttps
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class EmbeddedRepository(val indexUrl: String, val payload: String)

/** JVM resources are carried into the Android APK too; an empty URL store needs no setup. */
object EmbeddedRepositories {
    @Serializable
    private data class CatalogEntry(val resource: String, val indexUrl: String)

    fun load(): List<EmbeddedRepository> {
        val entries = Json.decodeFromString(ListSerializer(CatalogEntry.serializer()), resource("catalog.json"))
        require(entries.map { it.indexUrl }.distinct().size == entries.size) { "Duplicate embedded repositories" }
        return entries.map {
            requireHttps(it.indexUrl)
            require(it.resource.matches(Regex("[a-zA-Z0-9_/.\\-]+\\.json")) && !it.resource.contains(".."))
            EmbeddedRepository(it.indexUrl, resource(it.resource))
        }
    }

    private fun resource(name: String): String =
        requireNotNull(EmbeddedRepositories::class.java.getResourceAsStream("/yomira/repositories/$name")) {
            "Missing embedded repository resource: $name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
