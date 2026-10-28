package com.yomira.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yomira.data.extension.ExtensionConsent
import com.yomira.data.extension.ExtensionConsentCodec
import com.yomira.data.extension.ExtensionConsentStore
import kotlinx.coroutines.flow.first

/** Device-local inventory; settings reset clears it, cloud manifests cannot grant consent. */
class DataStoreExtensionConsentStore(private val dataStore: DataStore<Preferences>) : ExtensionConsentStore {
    private fun decode(values: Preferences): List<ExtensionConsent> =
        values[key]?.let { runCatching { ExtensionConsentCodec.decode(it) }.getOrDefault(emptyList()) } ?: emptyList()

    override suspend fun all(): List<ExtensionConsent> = decode(dataStore.data.first())
    override suspend fun grant(consent: ExtensionConsent) {
        dataStore.edit { values ->
            values[key] = ExtensionConsentCodec.encode(decode(values).filterNot { it.packageName == consent.packageName } + consent)
        }
    }
    override suspend fun revoke(packageName: String) {
        dataStore.edit { values ->
            values[key] = ExtensionConsentCodec.encode(decode(values).filterNot { it.packageName == packageName })
        }
    }
    private companion object { val key = stringPreferencesKey("extensionFirstUseConsents") }
}
