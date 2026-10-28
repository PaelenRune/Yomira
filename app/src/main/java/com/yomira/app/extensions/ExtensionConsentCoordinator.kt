package com.yomira.app.extensions

import com.yomira.data.extension.ExtensionConsent
import com.yomira.data.extension.ExtensionConsentRequester
import com.yomira.data.extension.ExtensionConsentStore
import com.yomira.data.extension.JsonSourceManifest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ExtensionConsentPrompt(val manifest: JsonSourceManifest, val answer: CompletableDeferred<String?>)

@Singleton
class ExtensionConsentCoordinator @Inject constructor(private val store: ExtensionConsentStore) : ExtensionConsentRequester {
    private val mutex = Mutex()
    private val pending = MutableStateFlow<ExtensionConsentPrompt?>(null)
    val prompt = pending.asStateFlow()
    private val declined = mutableSetOf<Triple<String, String?, Long>>()

    override suspend fun request(manifest: JsonSourceManifest): Boolean = mutex.withLock {
        val identity = Triple(manifest.packageName, manifest.publisherFingerprint, manifest.installedAt)
        if (identity in declined) return@withLock false
        if (store.all().any { it.packageName == manifest.packageName &&
                it.publisherFingerprint == manifest.publisherFingerprint &&
                (it.publisherFingerprint != null || it.grantedAt >= manifest.installedAt) }) return@withLock true
        val answer = CompletableDeferred<String?>()
        val prompt = ExtensionConsentPrompt(manifest, answer)
        pending.value = prompt
        try {
            val language = answer.await() ?: run { declined += identity; return@withLock false }
            store.grant(ExtensionConsent(manifest.packageName, manifest.publisherFingerprint,
                System.currentTimeMillis(), language))
            true
        } finally {
            pending.value = null
            answer.cancel()
        }
    }

    fun respond(prompt: ExtensionConsentPrompt, language: String?) {
        if (pending.value === prompt) prompt.answer.complete(language)
    }
}
