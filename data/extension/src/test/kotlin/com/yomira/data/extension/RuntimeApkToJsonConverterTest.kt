package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeApkToJsonConverterTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pkg = "eu.kanade.test"
    private val descriptor = ExtensionDescriptor(pkg, "Test", "tr", false, "2", 2,
        "https://example.org/test.apk", emptyList())

    @Test fun `converts manifest assets and standard DEX descriptors to portable JSON`() = runTest {
        val layout = SandboxLayout(temporary.newFolder().toPath())
        val apk = BackupTestFixtures.apk(mapOf("classes.dex" to BackupTestFixtures.dex(strings = listOf("Leu/kanade/test/TestExtension;"))), mapOf(
            "AndroidManifest.xml" to """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg" android:versionCode="2"><application><meta-data android:name="tachiyomi.extension.class" android:value=".ActualSource"/></application></manifest>""".toByteArray(),
            "assets/yomira/extension.json" to """{"packageName":"$pkg","entryClasses":["eu.kanade.test.AssetSource"]}""".toByteArray(),
        ))
        val manifest = RuntimeApkToJsonConverter(ApkSandboxExtractor(layout)).convert(apk, descriptor, "https://example.org/index.json", 4)
        assertTrue(manifest.entryClasses.contains("eu.kanade.test.ActualSource"))
        assertTrue(manifest.entryClasses.contains("eu.kanade.test.AssetSource"))
        assertTrue(manifest.entryClasses.contains("eu/kanade/test/TestExtension"))
        assertEquals(pkg, manifest.apkMetadata!!.packageName)
        assertEquals(2L, manifest.apkMetadata!!.versionCode)
        assertEquals("assets/yomira/extension.json", manifest.apkMetadata!!.assets.single().name)
        assertEquals(manifest, JsonSourceManifestCodec.decode(JsonSourceManifestCodec.encode(manifest)))
    }

    @Test fun `mismatched package does not replace existing DEX`() = runTest {
        val layout = SandboxLayout(temporary.newFolder().toPath())
        val extractor = ApkSandboxExtractor(layout)
        extractor.extract(BackupTestFixtures.apkWithSingleDex(pkg), pkg, 1)
        val before = Files.readAllBytes(layout.dexDirectory(pkg).resolve("classes.dex"))
        val invalid = BackupTestFixtures.apk(mapOf("classes.dex" to BackupTestFixtures.dex(strings = emptyList())),
            mapOf("AndroidManifest.xml" to """<manifest package="other.package"/>""".toByteArray()))
        try {
            extractor.extract(invalid, pkg, 2)
            fail("Package mismatch must fail")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, Files.readAllBytes(layout.dexDirectory(pkg).resolve("classes.dex")))
    }

    @Test fun `reads Android binary XML string pool and typed version code`() {
        val strings = listOf("manifest", "package", pkg, "versionCode")
        val data = strings.flatMap { text -> listOf(text.length.toByte(), text.length.toByte()) + text.toByteArray().toList() + 0.toByte() }.toByteArray()
        val poolSize = 28 + strings.size * 4 + data.size
        val xml = ByteBuffer.allocate(8 + poolSize + 76).order(ByteOrder.LITTLE_ENDIAN)
        xml.putShort(3).putShort(8).putInt(xml.capacity())
        xml.putShort(1).putShort(28).putInt(poolSize).putInt(strings.size).putInt(0).putInt(0x100)
            .putInt(28 + strings.size * 4).putInt(0)
        var offset = 0
        strings.forEach { xml.putInt(offset); offset += it.length + 3 }
        xml.put(data)
        xml.putShort(0x102).putShort(16).putInt(76).putInt(1).putInt(-1)
        xml.putInt(-1).putInt(0).putShort(20).putShort(20).putShort(2).putShort(0).putShort(0).putShort(0)
        xml.putInt(-1).putInt(1).putInt(2).putShort(8).put(0).put(3).putInt(2)
        xml.putInt(-1).putInt(3).putInt(-1).putShort(8).put(0).put(0x10).putInt(2)
        val metadata = ApkMetadataInspector.manifest(xml.array())
        assertEquals(pkg, metadata.packageName)
        assertEquals(2L, metadata.versionCode)
    }
}
