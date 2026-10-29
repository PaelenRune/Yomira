package com.yomira.data.extension

import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.domain.extension.ExtensionLogo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class EmbeddedRepository(
    val indexUrl: String,
    val payload: String,
    val upstreamUrl: String? = null,
)

/**
 * The personal build ships with curated repositories: the full Keiyoushi catalog plus the
 * Turkish and Western slices, packaged as committed snapshots so the list is usable offline
 * from the first launch. [DEFAULTS] is what a fresh install registers; the slices are opt-in
 * alternatives for a narrower scope. Users can still disable any curated index or register
 * their own; regenerate the snapshots with `scripts/update-curated-repositories.ps1`.
 */
object EmbeddedRepositories {
    const val UPSTREAM_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/main/index.json"

    /**
     * Indexes registered on a fresh install. The universal catalog already contains every
     * Turkish and Western source, so registering a slice beside it would only duplicate
     * package ownership; slices stay opt-in for users who disable the universal index.
     */
    val DEFAULTS: List<String> = listOf(OnboardingWizardManager.KEIYOUSHI_INDEX)

    /** Curated slices of [DEFAULTS]; their packages yield to any other registered index. */
    val CURATED_SLICES: Set<String> = setOf(
        OnboardingWizardManager.TURKISH_INDEX,
        OnboardingWizardManager.WESTERN_INDEX,
    )

    fun load(): List<EmbeddedRepository> = listOf(
        embedded("/assets/keiyoushi/index.min.json", OnboardingWizardManager.KEIYOUSHI_INDEX),
        embedded("/assets/repositories/turkish/index.min.json", OnboardingWizardManager.TURKISH_INDEX),
        embedded("/assets/repositories/western/index.min.json", OnboardingWizardManager.WESTERN_INDEX),
    )

    private fun embedded(resource: String, indexUrl: String): EmbeddedRepository {
        val payload = requireNotNull(javaClass.getResourceAsStream(resource)) {
            "Missing bundled repository snapshot: $resource"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return EmbeddedRepository(indexUrl, payload, UPSTREAM_URL)
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
                ExtensionLogo.resolveIcon(entry.getValue("resources").jsonObject["iconUrl"]?.jsonPrimitive?.contentOrNull,
                    UPSTREAM_URL)?.let { put("iconUrl", it) }
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
