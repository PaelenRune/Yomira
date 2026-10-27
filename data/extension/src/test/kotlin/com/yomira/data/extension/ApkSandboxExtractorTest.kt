package com.yomira.data.extension

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkSandboxExtractorTest {
    @get:Rule val temporary = TemporaryFolder()

    private val packageOne = "eu.kanade.tachiyomi.extension.tr.ornek"
    private fun layout() = SandboxLayout(temporary.newFolder().toPath())

    @Test fun `extracts every dex payload into the private sandbox and hashes it`() = runTest {
        val layout = layout()
        val firstDex = BackupTestFixtures.dexWithEntryClass(packageOne)
        val secondDex = BackupTestFixtures.dex(
            version = "038",
            strings = listOf("eu/kanade/tachiyomi/extension/tr/ornek/IkinciExtension"),
        )
        val apk = BackupTestFixtures.apk(
            dexEntries = mapOf("classes.dex" to firstDex, "classes2.dex" to secondDex),
            extras = mapOf("AndroidManifest.xml" to """<manifest package="$packageOne"/>""".toByteArray(), "resources.arsc" to byteArrayOf(4, 5, 6)),
        )

        val extraction = ApkSandboxExtractor(layout).extract(apk, packageOne, installedAt = 55L)

        assertEquals(listOf("classes.dex", "classes2.dex"), extraction.dexFiles.map { it.name })
        assertEquals(firstDex.size.toLong(), extraction.dexFiles.first().sizeBytes)
        extraction.dexFiles.forEach { entry ->
            assertTrue(entry.sha256.matches(Regex("^[0-9a-f]{64}$")))
            val onDisk = Files.readAllBytes(extraction.sandboxDirectory.resolve(entry.name))
            assertEquals(entry.sha256, ApkSandboxExtractor.sha256Hex(onDisk))
            assertEquals(entry.sizeBytes, Files.size(extraction.sandboxDirectory.resolve(entry.name)))
        }
        // Binary AndroidManifest and resources are ignored; entry classes come from the string table.
        assertEquals(
            listOf(
                "eu/kanade/tachiyomi/extension/tr/ornek/IkinciExtension",
                "eu/kanade/tachiyomi/extension/tr/ornek/OrnekExtension",
            ),
            extraction.entryClassCandidates,
        )
        assertEquals(55L, extraction.installedAt)
    }

    @Test fun `reinstalling removes dex files the new version no longer ships`() = runTest {
        val layout = layout()
        val oldApk = BackupTestFixtures.apk(
            mapOf(
                "classes.dex" to BackupTestFixtures.dexWithEntryClass(packageOne),
                "classes2.dex" to BackupTestFixtures.dex(strings = listOf("eu/kanade/tachiyomi/extension/tr/ornek/EskiExtension")),
            ),
        )
        val newApk = BackupTestFixtures.apkWithSingleDex(packageOne)
        val extractor = ApkSandboxExtractor(layout)

        extractor.extract(oldApk, packageOne, 1L)
        assertTrue(Files.exists(layout.dexDirectory(packageOne).resolve("classes2.dex")))

        val second = extractor.extract(newApk, packageOne, 2L)
        assertEquals(listOf("classes.dex"), second.dexFiles.map { it.name })
        assertFalse(Files.exists(layout.dexDirectory(packageOne).resolve("classes2.dex")))
    }

    @Test fun `zip slip style entries are rejected`() = runTest {
        val evil = BackupTestFixtures.apk(mapOf("assets/../../classes.dex" to BackupTestFixtures.dexWithEntryClass(packageOne)))
        try {
            ApkSandboxExtractor(layout()).extract(evil, packageOne, 1L)
            fail("Path traversal entries must be refused")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("Unsafe archive entry"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `archive without dex is not an extension`() = runTest {
        val noDex = BackupTestFixtures.apk(mapOf("res/drawable/icon.png" to byteArrayOf(1, 2, 3)))
        ApkSandboxExtractor(layout()).extract(noDex, packageOne, 1L)
    }

    @Test(expected = java.io.IOException::class)
    fun `dex over the per-file quota is rejected`() = runTest {
        val fat = BackupTestFixtures.apk(mapOf("classes.dex" to ByteArray(4096) { (it % 251 + 1).toByte() }))
        ApkSandboxExtractor(layout(), maxDexBytesPerFile = 1024L).extract(fat, packageOne, 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `corrupt dex magic fails header inspection`() {
        DexHeader.inspect(ByteArray(0x80) { (it % 31 + 1).toByte() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dex file size must match the payload exactly`() {
        val dex = BackupTestFixtures.dexWithEntryClass(packageOne)
        DexHeader.inspect(dex + byteArrayOf(0, 0, 0, 0))
    }

    @Test fun `headerless string table still inspects safely`() {
        // A 0-size string table is legal; inspection must still succeed with no candidates.
        val bare = BackupTestFixtures.dex(strings = emptyList())
        val inspection = DexHeader.inspect(bare)
        assertEquals("039", inspection.version)
        assertTrue(inspection.entryClassCandidates.isEmpty())
    }
}
