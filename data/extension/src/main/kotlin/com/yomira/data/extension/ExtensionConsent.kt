package com.yomira.data.extension

import com.yomira.domain.extension.ReaderPreferences
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ExtensionConsent(
    val packageName: String,
    val publisherFingerprint: String?,
    val grantedAt: Long,
    val language: String,
)

interface ExtensionConsentStore {
    suspend fun all(): List<ExtensionConsent>
    suspend fun grant(consent: ExtensionConsent)
    suspend fun revoke(packageName: String)
}

object ExtensionConsentCodec {
    private val serializer = ListSerializer(ExtensionConsent.serializer())
    fun encode(records: List<ExtensionConsent>): String = Json.encodeToString(serializer, records)
    fun decode(text: String): List<ExtensionConsent> = Json.decodeFromString(serializer, text).also { records ->
        require(records.map { it.packageName }.distinct().size == records.size)
        records.forEach {
            require(it.packageName.isNotBlank() && it.grantedAt >= 0 && it.language in setOf("tr", "en"))
            require(it.publisherFingerprint == null || it.publisherFingerprint.matches(Regex("[0-9a-f]{64}")))
        }
    }
}

/** Only foreground reading opts in to prompting; background reads fail closed. */
class ExtensionConsentInteraction(val preferences: ReaderPreferences) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ExtensionConsentInteraction>
}

fun interface ExtensionConsentRequester {
    suspend fun request(manifest: JsonSourceManifest): Boolean
}

class ExtensionTrust(private val store: ExtensionConsentStore, private val requester: ExtensionConsentRequester) {
    suspend fun isGranted(manifest: JsonSourceManifest): Boolean = store.all().any {
        it.packageName == manifest.packageName && it.publisherFingerprint == manifest.publisherFingerprint &&
            // Unknown publishers cannot be bound to a signer: re-prompt after reinstall/update.
            (it.publisherFingerprint != null || it.grantedAt >= manifest.installedAt)
    }

    suspend fun allow(manifest: JsonSourceManifest, interaction: ExtensionConsentInteraction?): Boolean {
        if (isGranted(manifest)) return true
        val preferences = interaction?.preferences ?: return false
        if ((!preferences.allowAdultContent && manifest.nsfw) ||
            manifest.sources.none { preferences.language.accepts(it.language) }) return false
        return requester.request(manifest)
    }
}
