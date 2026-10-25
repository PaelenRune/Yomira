package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import com.yomira.domain.extension.ExtensionSource
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SandboxExtensionInstallerTest {
    @get:Rule val temporary = TemporaryFolder()

    private val packageOne = "eu.kanade.tachiyomi.extension.tr.ornek"
    private val descriptor = ExtensionDescriptor(
        packageName = packageOne,
        name = "Örnek",
        language = "tr",
        nsfw = false,
        versionName = "1.4.0",
        versionCode = 14,
        apkUrl = "https://repo.example.org/apk/ornek.tr.apk",
        sources = listOf(ExtensionSource("1769787359133808834", "Örnek", "tr")),
    )

    private inner class Harness(apkPayload: ByteArray) {
        val layout = SandboxLayout(temporary.newFolder().toPath())
        val store = FileJsonSourceManifestStore(layout)
        var payload = apkPayload
        private var downloads = 0
        private val clockValues = longArrayOf(1_700_000_000_000L, 1_700_000_001_000L, 1_700_000_002_000L, 1_700_000_003_000L)
        val installer = SandboxExtensionInstaller(
            layout = layout,
            store = store,
            download = ExtensionDownloadClient { _ -> payload.also { downloads++ } },
            clock = { clockValues[(downloads - 1).coerceIn(0, clockValues.lastIndex)] },
        )
        val guard = SandboxIntegrityGuard(layout, store)
        val provider = ManifestInstalledExtensionProvider(store)
    }

    @Test fun `install keeps the apk out of the package manager and records a manifest`() = runTest {
        val harness = Harness(BackupTestFixtures.apkWithSingleDex(packageOne))

        val manifest = harness.installer.install(descriptor, "https://repo.example.org/index.min.json")

        assertEquals(packageOne, manifest.packageName)
        assertEquals(14L, manifest.versionCode)
        assertEquals("https://repo.example.org/index.min.json", manifest.repositoryUrl)
        assertEquals(listOf("classes.dex"), manifest.dexFiles.map { it.name })
        assertEquals(listOf("eu/kanade/tachiyomi/extension/tr/ornek/OrnekExtension"), manifest.entryClasses)
        assertEquals(manifest, harness.store.find(packageOne))

        // Nothing installed system-wide: only the private sandbox tree exists.
        assertTrue(Files.exists(harness.layout.dexDirectory(packageOne).resolve("classes.dex")))
        assertEquals(1, harness.provider.installed().size)
        assertEquals(14L, harness.provider.installed().single().versionCode)
    }

    @Test fun `same version cannot be installed twice but upgrades work`() = runTest {
        val harness = Harness(BackupTestFixtures.apkWithSingleDex(packageOne))
        harness.installer.install(descriptor, "https://repo.example.org/index.min.json")
        try {
            harness.installer.install(descriptor, "https://repo.example.org/index.min.json")
            fail("Reinstalling the same version must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("already installed"))
        }

        val upgraded = harness.installer.install(
            descriptor.copy(versionCode = 15, versionName = "1.5.0"),
            "https://repo.example.org/index.min.json",
        )
        assertEquals(15L, upgraded.versionCode)
        assertEquals(15L, harness.provider.installed().single().versionCode)
    }

    @Test fun `failed install never leaves a manifest behind`() = runTest {
        val harness = Harness("this is not a zip".toByteArray())
        try {
            harness.installer.install(descriptor, "https://repo.example.org/index.min.json")
            fail("Garbage payload must be rejected")
        } catch (expected: Exception) {
            assertTrue(harness.provider.installed().isEmpty())
            assertNull(harness.store.find(packageOne))
        }
    }

    @Test fun `integrity guard refuses tampered dex and accepts pristine one`() = runTest {
        val harness = Harness(BackupTestFixtures.apkWithSingleDex(packageOne))
        harness.installer.install(descriptor, "https://repo.example.org/index.min.json")

        val loadable = harness.guard.requireLoadable(packageOne)
        assertEquals(1, loadable.dexPaths.size)
        assertTrue(Files.isRegularFile(loadable.dexPaths.single()))

        val victim = loadable.dexPaths.single()
        val pristine = Files.readAllBytes(victim)
        Files.write(victim, pristine.copyOf().also { it[0x100] = (it[0x100].toInt() xor 0xFF).toByte() })
        try {
            harness.guard.requireLoadable(packageOne)
            fail("Swapped dex bytes must not load")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("hash"))
        }

        Files.write(victim, pristine)
        assertNotNull(harness.guard.requireLoadable(packageOne))
    }

    @Test fun `guard rejects packages without a manifest`() = runTest {
        val harness = Harness(BackupTestFixtures.apkWithSingleDex(packageOne))
        try {
            harness.guard.requireLoadable("eu.ghost.extension")
            fail("Unknown package must not load")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("manifest"))
        }
    }

    @Test fun `uninstall wipes both manifest and dex cache`() = runTest {
        val harness = Harness(BackupTestFixtures.apkWithSingleDex(packageOne))
        harness.installer.install(descriptor, "https://repo.example.org/index.min.json")

        assertTrue(harness.installer.uninstall(packageOne))

        assertNull(harness.store.find(packageOne))
        assertTrue(harness.provider.installed().isEmpty())
        assertFalse(Files.exists(harness.layout.dexDirectory(packageOne).resolve("classes.dex")))
        assertFalse(harness.installer.uninstall(packageOne))
    }

    @Test fun `drive-restored manifest rehydrates only when hashes match exactly`() = runTest {
        val apk = BackupTestFixtures.apkWithSingleDex(packageOne)
        val source = Harness(apk)
        val manifest = source.installer.install(descriptor, "https://repo.example.org/index.min.json")

        // Fresh device: manifest arrives through Drive, APK is re-downloaded and verified.
        val fresh = Harness(apk)
        fresh.store.save(manifest)
        val loadable = fresh.installer.rehydrate(manifest)
        assertNotNull(loadable)
        assertEquals(manifest.packageName, loadable!!.manifest.packageName)
        assertTrue(loadable.dexPaths.all { Files.isRegularFile(it) })

        // A malicious repository now serves different bytes: trust is lost, nothing loads.
        val hostile = Harness(BackupTestFixtures.apkWithSingleDex("eu.kanade.tachiyomi.extension.tr.kotu"))
        hostile.store.save(manifest)
        assertNull(hostile.installer.rehydrate(manifest))
        assertFalse(Files.exists(hostile.layout.dexDirectory(packageOne).resolve("classes.dex")))
    }
}
