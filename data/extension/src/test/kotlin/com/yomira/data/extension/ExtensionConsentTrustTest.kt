package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionSource
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** First-use consent rules: fail closed, persist grants, re-prompt exactly when identity is unknown. */
class ExtensionConsentTrustTest {
    @get:Rule val temporary = TemporaryFolder()

    private val dex = SandboxedDexFile("classes.dex", 4_096L, "a".repeat(64))
    private val descriptor = ExtensionDescriptor(
        packageName = "eu.kanade.tachiyomi.extension.tr.ornek",
        name = "Örnek", language = "tr", nsfw = false, versionName = "1.4.0", versionCode = 14,
        apkUrl = "https://repo.example.org/apk/ornek.tr.apk",
        sources = listOf(ExtensionSource("7980140199189800807", "Örnek", "tr")),
    )
    private val fingerprint = "54da2d6e1de01e30847e5a1c855221cb8a72fff2be111ea695488c855f0b1ae3"
    private val preferences = ReaderPreferences(ReadingLanguage.BOTH)

    private fun manifest(installedAt: Long = 1_000L, publisherFingerprint: String? = fingerprint,
        nsfw: Boolean = false) = JsonSourceManifest.of(
        descriptor = descriptor.copy(nsfw = nsfw), repositoryUrl = "https://repo.example.org/index.min.json",
        installedAt = installedAt, dexFiles = listOf(dex),
    ).copy(publisherFingerprint = publisherFingerprint)

    private class MemoryConsentStore : ExtensionConsentStore {
        val records = mutableListOf<ExtensionConsent>()
        var granted = 0
        override suspend fun all(): List<ExtensionConsent> = records.toList()
        override suspend fun grant(consent: ExtensionConsent) { granted++; records.removeAll { it.packageName == consent.packageName }; records += consent }
        override suspend fun revoke(packageName: String) { records.removeAll { it.packageName == packageName } }
    }

    /** Mirrors the production coordinator: an approved prompt persists its own consent record. */
    private fun approving(store: ExtensionConsentStore, grantAt: Long = 2_500L, onPrompt: () -> Unit = {}): ExtensionConsentRequester =
        ExtensionConsentRequester { approved ->
            onPrompt()
            store.grant(ExtensionConsent(approved.packageName, approved.publisherFingerprint, grantAt, "tr"))
            true
        }

    @Test fun `declined consent leaves the source untrusted so matching treats it as unavailable`() = runTest {
        val layout = SandboxLayout(temporary.newFolder().toPath())
        val store = FileJsonSourceManifestStore(layout)
        val manifest = manifest()
        store.save(manifest)
        var prompts = 0
        val provider = ManifestInstalledExtensionProvider(store, ExtensionTrust(MemoryConsentStore()) { prompts++; false })

        // Foreground read with an interaction element: the prompt is shown and declined.
        val installed = withContext(ExtensionConsentInteraction(preferences)) { provider.installed() }
        assertEquals(1, prompts)
        assertFalse(installed.single().trusted)
        // SmartSourceMatcher only queries trusted installations (see SmartSourceMatcherTest),
        // so a declined extension is reported through the existing unavailable copy.
    }

    @Test fun `granted consent persists and keeps the source trusted in background reads`() = runTest {
        val layout = SandboxLayout(temporary.newFolder().toPath())
        val store = FileJsonSourceManifestStore(layout)
        val consentStore = MemoryConsentStore()
        val manifest = manifest()
        store.save(manifest)
        var prompts = 0
        val provider = ManifestInstalledExtensionProvider(store, ExtensionTrust(consentStore, approving(consentStore) { prompts++ }))

        assertTrue(withContext(ExtensionConsentInteraction(preferences)) { provider.installed() }.single().trusted)
        assertEquals(1, prompts)
        assertEquals(1, consentStore.granted)

        // No interaction element (extensions list, library refresh): still trusted, never re-prompted.
        assertTrue(provider.installed().single().trusted)
        assertEquals(1, prompts)

        // Consent survives a fresh trust instance reading the same persisted records.
        assertTrue(ManifestInstalledExtensionProvider(store, ExtensionTrust(consentStore) { prompts++; false })
            .installed().single().trusted)
        assertEquals(1, prompts)
    }

    @Test fun `unconsented sources fail closed in background reads without any prompt`() = runTest {
        val layout = SandboxLayout(temporary.newFolder().toPath())
        val store = FileJsonSourceManifestStore(layout)
        store.save(manifest())
        var prompts = 0
        val provider = ManifestInstalledExtensionProvider(store, ExtensionTrust(MemoryConsentStore()) { prompts++; true })
        // No interaction element in the context: no dialog from a background surface.
        assertFalse(provider.installed().single().trusted)
        assertEquals(0, prompts)
    }

    @Test fun `legacy manifests without fingerprints re-prompt after reinstall or update`() = runTest {
        val consentStore = MemoryConsentStore()
        val trust = ExtensionTrust(consentStore, approving(consentStore))
        val legacy = manifest(installedAt = 2_000L, publisherFingerprint = null)
        assertTrue(trust.allow(legacy, ExtensionConsentInteraction(preferences)))

        // Same install stays granted...
        assertTrue(trust.allow(legacy, null))
        // ...but a newer install of an unverifiable package must ask again.
        assertFalse(trust.allow(legacy.copy(installedAt = 3_000L), null))
        assertTrue(trust.allow(legacy.copy(installedAt = 3_000L), ExtensionConsentInteraction(preferences)))
    }

    @Test fun `fingerprint bound consent survives same-signer updates`() = runTest {
        val consentStore = MemoryConsentStore()
        var prompts = 0
        val trust = ExtensionTrust(consentStore, approving(consentStore) { prompts++ })
        assertTrue(trust.allow(manifest(installedAt = 1_000L), ExtensionConsentInteraction(preferences)))
        // A newer version signed with the same key keeps the grant: identity is still pinned.
        assertTrue(trust.allow(manifest(installedAt = 9_000L), null))
        assertEquals(1, prompts)
        // A different signer under the same package name is a different publisher.
        assertFalse(trust.allow(manifest(installedAt = 9_000L, publisherFingerprint = "b".repeat(64)), null))
    }

    @Test fun `adult and language policies skip prompting entirely`() = runTest {
        var prompts = 0
        val trust = ExtensionTrust(MemoryConsentStore()) { prompts++; true }
        // Adult extension under a safe profile: unavailable without ever showing a dialog.
        assertFalse(trust.allow(manifest(nsfw = true), ExtensionConsentInteraction(ReaderPreferences(ReadingLanguage.BOTH))))
        // No source in the reader's language: same fail-closed, prompt-free behavior.
        assertFalse(trust.allow(manifest(), ExtensionConsentInteraction(ReaderPreferences(ReadingLanguage.ENGLISH, allowAdultContent = true))))
        assertEquals(0, prompts)
    }

    @Test fun `interaction element travels through the coroutine context`() = runTest {
        withContext(ExtensionConsentInteraction(preferences)) {
            assertEquals(preferences, currentCoroutineContext()[ExtensionConsentInteraction]?.preferences)
        }
        assertNull(currentCoroutineContext()[ExtensionConsentInteraction])
    }

    @Test fun `consent records round trip through the codec and invalid ones are refused`() {
        val record = ExtensionConsent("eu.kanade.test", fingerprint, 1_700_000_000_000L, "tr")
        assertEquals(listOf(record), ExtensionConsentCodec.decode(ExtensionConsentCodec.encode(listOf(record))))
        assertNull(ExtensionConsentCodec.decode("[]").singleOrNull())
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionConsentCodec.decode(ExtensionConsentCodec.encode(listOf(record.copy(language = "de"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionConsentCodec.decode(ExtensionConsentCodec.encode(listOf(record.copy(publisherFingerprint = "nothex"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionConsentCodec.decode(ExtensionConsentCodec.encode(listOf(record, record.copy(grantedAt = 1L))))
        }
    }
}
