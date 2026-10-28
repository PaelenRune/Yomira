package com.yomira.app.extensions

import com.yomira.data.extension.ExtensionConsent
import com.yomira.data.extension.ExtensionConsentStore
import com.yomira.data.extension.JsonSourceManifest
import com.yomira.data.extension.SandboxedDexFile
import com.yomira.domain.extension.ExtensionDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionConsentCoordinatorTest {
    private val manifest = JsonSourceManifest.of(
        ExtensionDescriptor("eu.kanade.test", "Test", "tr", false, "1", 1,
            "https://example.org/test.apk", emptyList()),
        "https://example.org/index.json", 1_000L, listOf(SandboxedDexFile("classes.dex", 4L, "a".repeat(64))),
    ).copy(publisherFingerprint = "54da2d6e1de01e30847e5a1c855221cb8a72fff2be111ea695488c855f0b1ae3")

    private class MemoryStore : ExtensionConsentStore {
        val records = mutableListOf<ExtensionConsent>()
        override suspend fun all(): List<ExtensionConsent> = records.toList()
        override suspend fun grant(consent: ExtensionConsent) { records += consent }
        override suspend fun revoke(packageName: String) { records.removeAll { it.packageName == packageName } }
    }

    @Test fun `allow persists the consent with the language of the accepted copy`() = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryStore()
        val coordinator = ExtensionConsentCoordinator(store)
        var granted = false
        val job = launch { granted = coordinator.request(manifest) }
        val prompt = checkNotNull(coordinator.prompt.value) { "prompt must be published while awaiting the reader" }
        assertEquals(manifest, prompt.manifest)
        coordinator.respond(prompt, "tr")
        job.join()
        assertTrue(granted)
        val record = store.records.single()
        assertEquals(manifest.packageName, record.packageName)
        assertEquals(manifest.publisherFingerprint, record.publisherFingerprint)
        assertEquals("tr", record.language)
        assertTrue(record.grantedAt > 0)
        assertNull(coordinator.prompt.value)
    }

    @Test fun `decline answers false and memoizes the refusal for the session`() = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryStore()
        val coordinator = ExtensionConsentCoordinator(store)
        var first = true
        val declined = launch { first = coordinator.request(manifest) }
        coordinator.respond(coordinator.prompt.value!!, null)
        declined.join()
        assertFalse(first)
        assertTrue(store.records.isEmpty())

        // Same identity never re-prompts within the session; a declined source stays unavailable.
        var second = true
        val memoized = launch { second = coordinator.request(manifest) }
        memoized.join()
        assertFalse(second)
        assertNull(coordinator.prompt.value)
    }

    @Test fun `an already granted package never prompts again`() = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryStore().apply {
            records += ExtensionConsent(manifest.packageName, manifest.publisherFingerprint, 999L, "en")
        }
        val coordinator = ExtensionConsentCoordinator(store)
        var granted = false
        val job = launch { granted = coordinator.request(manifest) }
        job.join()
        assertTrue(granted)
        assertNull(coordinator.prompt.value)
    }
}
