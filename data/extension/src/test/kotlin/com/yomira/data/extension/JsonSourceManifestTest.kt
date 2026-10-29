package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionSource
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonSourceManifestTest {
    @get:Rule val temporary = TemporaryFolder()

    private val dex = SandboxedDexFile("classes.dex", 4_096L, "a".repeat(64))
    private val descriptor = ExtensionDescriptor(
        packageName = "eu.kanade.tachiyomi.extension.tr.ornek",
        name = "Örnek",
        language = "tr",
        nsfw = false,
        versionName = "1.4.0",
        versionCode = 14,
        apkUrl = "https://repo.example.org/apk/ornek.tr.apk",
        sources = listOf(ExtensionSource("7980140199189800807", "Örnek", "tr")),
    )

    private fun manifest(versionCode: Long = 14, installedAt: Long = 1_700_000_000_000L) = JsonSourceManifest.of(
        descriptor = descriptor.copy(versionCode = versionCode),
        repositoryUrl = "https://repo.example.org/index.min.json",
        installedAt = installedAt,
        dexFiles = listOf(dex),
        entryClasses = listOf("eu/kanade/tachiyomi/extension/tr/ornek/OrnekExtension"),
    )

    @Test fun `manifest survives a JSON round trip unchanged`() {
        val original = manifest()
        val restored = JsonSourceManifestCodec.decode(JsonSourceManifestCodec.encode(original))
        assertEquals(original, restored)
        assertEquals(original.sources, restored.sources)
        assertEquals(original.dexFiles.single().sha256, restored.dexFiles.single().sha256)
        assertEquals(
            original.sources.map { Triple(it.id, it.name, it.language) },
            restored.descriptor().sources.map { Triple(it.id, it.name, it.language) },
        )
    }

    @Test fun `installed icon and origin survive offline manifest round trip`() {
        val withIcon = JsonSourceManifest.of(descriptor.copy(iconUrl = "https://cdn.test/site.png"),
            "https://repo.example.org/index.json", 0, listOf(dex))
        val restored = JsonSourceManifestCodec.decode(JsonSourceManifestCodec.encode(withIcon)).descriptor()
        assertEquals(withIcon.iconUrl, restored.iconUrl)
        assertEquals(withIcon.repositoryUrl, restored.repositoryUrl)
    }

    @Test fun `legacy manifests without icon still offer repository fallback`() {
        val legacy = JsonSourceManifestCodec.encode(manifest()).replace(",\"iconUrl\":null", "")
        val restored = JsonSourceManifestCodec.decode(legacy).descriptor()
        assertNull(restored.iconUrl)
        assertEquals("https://repo.example.org/index.min.json", restored.repositoryUrl)
    }

    @Test fun `legacy manifests without publisher fields decode as an unknown publisher`() {
        // Exactly what an older app version wrote before publisher identity existed:
        // the same document with the two additive fields stripped.
        val legacy = JsonSourceManifestCodec.encode(manifest())
            .replace(""","publisherFingerprint":null,"publisherFingerprintReason":null""", "")
        val decoded = JsonSourceManifestCodec.decode(legacy)
        assertNull(decoded.publisherFingerprint)
        assertNull(decoded.publisherFingerprintReason)
        assertEquals(manifest().normalized(), decoded.normalized())
    }

    @Test fun `encoded output is deterministic regardless of input order`() {
        val shuffled = manifest().copy(
            dexFiles = listOf(SandboxedDexFile("classes2.dex", 2L, "b".repeat(64)), dex),
            entryClasses = listOf("z/ZExtension", "a/AExtension"),
            sources = listOf(
                ManifestSourceEntry("9", "B", "tr"),
                ManifestSourceEntry("1", "A", "tr"),
            ),
        )
        val first = JsonSourceManifestCodec.encode(shuffled)
        val second = JsonSourceManifestCodec.encode(shuffled.copy(
            dexFiles = shuffled.dexFiles.reversed(),
            entryClasses = shuffled.entryClasses.reversed(),
            sources = shuffled.sources.reversed(),
        ))
        assertEquals(first, second)
    }

    @Test fun `index export and import round trips every manifest`() {
        val manifests = listOf(manifest(), manifest(versionCode = 3).copy(packageName = "org.other.pkg"))
        val decoded = JsonSourceManifestCodec.decodeIndex(JsonSourceManifestCodec.encodeIndex(manifests))
        assertEquals(manifests.map { it.normalized() }.sortedBy { it.packageName }, decoded.sortedBy { it.packageName })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsigned dex fingerprints are rejected`() {
        manifest().copy(dexFiles = listOf(dex.copy(sha256 = "not-a-hash")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `insecure apk url cannot be validated`() {
        manifest().copy(apkUrl = "http://repo.example.org/apk.apk").validate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `future schema versions are refused`() {
        manifest().copy(schemaVersion = 99).validate()
    }

    @Test fun `codec refuses unknown fields so schema drift is loud`() {
        val text = JsonSourceManifestCodec.encode(manifest())
            .removeSuffix("}")
            .plus("\"someNewField\":true}")
        try {
            JsonSourceManifestCodec.decode(text)
            fail("Unknown fields must not be silently accepted")
        } catch (expected: Exception) {
            assertTrue(expected.message != null || expected.cause != null)
        }
    }

    @Test fun `store persists, lists and deletes manifests atomically`() = runTest {
        val layout = SandboxLayout(temporary.root.toPath().resolve("sandbox"))
        val store = FileJsonSourceManifestStore(layout)
        assertTrue(store.all().isEmpty())
        assertNull(store.find(descriptor.packageName))

        store.save(manifest())
        assertEquals(manifest(), store.find(descriptor.packageName))
        assertEquals(listOf(manifest()), store.all())
        assertTrue(Files.exists(layout.manifestFile(descriptor.packageName)))

        assertTrue(store.delete(descriptor.packageName))
        assertNull(store.find(descriptor.packageName))
        assertFalse(store.delete(descriptor.packageName))
    }

    @Test fun `drive index import keeps newest version and never downgrades`() = runTest {
        val layout = SandboxLayout(temporary.root.toPath().resolve("sandbox"))
        val store = FileJsonSourceManifestStore(layout)
        store.save(manifest(versionCode = 20, installedAt = 2_000L))

        val cloudIndex = JsonSourceManifestCodec.encodeIndex(listOf(
            manifest(versionCode = 10, installedAt = 1_000L), // stale cloud copy
            manifest(versionCode = 30, installedAt = 3_000L)
                .copy(packageName = "org.other.pkg"), // brand-new package
        ))
        val written = store.importIndex(cloudIndex)

        assertEquals(1, written) // only the new package lands; the stale copy loses
        assertEquals(20L, store.find(descriptor.packageName)!!.versionCode)
        assertEquals(30L, store.find("org.other.pkg")!!.versionCode)

        val indexFile = store.writeIndexFile()
        assertTrue(Files.exists(indexFile))
        assertEquals(2, JsonSourceManifestCodec.decodeIndex(Files.readString(indexFile)).size)
    }

    @Test fun `manifest backed provider replaces the package manager view`() = runTest {
        val layout = SandboxLayout(temporary.root.toPath().resolve("sandbox"))
        val store = FileJsonSourceManifestStore(layout)
        val provider = ManifestInstalledExtensionProvider(store)
        assertTrue(provider.installed().isEmpty())
        store.save(manifest())
        val installed = provider.installed().single()
        assertEquals(descriptor.packageName, installed.packageName)
        assertEquals(14L, installed.versionCode)
        assertFalse(installed.trusted)
        assertEquals(descriptor.copy(repositoryUrl = manifest().repositoryUrl), installed.descriptor)
    }
}
