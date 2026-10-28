package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Publisher identity extraction against a real PKCS#7 signature block: the fixture pair
 * (FIXTURE.RSA + publisher.der) was produced by `jarsigner`/`keytool` from a JDK-generated
 * key, so these tests exercise the exact parsing an in-the-wild v1-signed APK needs.
 */
class ApkPublisherIdentityTest {
    @get:Rule val temporary = TemporaryFolder()
    private val signatureBlock = javaClass.getResourceAsStream("/publisher/FIXTURE.RSA")!!.readBytes()
    private val publisherDer = javaClass.getResourceAsStream("/publisher/publisher.der")!!.readBytes()

    @Test fun `parses the PKCS 7 block into the signer certificate fingerprint`() {
        val fingerprints = ApkPublisherInspector.parseSignatureBlock(signatureBlock)
        assertEquals(listOf(ApkSandboxExtractor.sha256Hex(publisherDer)), fingerprints)
        // Pinned literal: proves the checked-in fixture is the one this test was built around.
        assertEquals("54da2d6e1de01e30847e5a1c855221cb8a72fff2be111ea695488c855f0b1ae3", fingerprints.single())
    }

    @Test fun `v1 signed apk yields the signer fingerprint`() {
        val apk = BackupTestFixtures.apk(
            dexEntries = mapOf("classes.dex" to BackupTestFixtures.dexWithEntryClass("eu.kanade.test")),
            extras = mapOf(
                "AndroidManifest.xml" to """<manifest package="eu.kanade.test"/>""".toByteArray(),
                "META-INF/FIXTURE.RSA" to signatureBlock,
            ),
        )
        val identity = ApkPublisherInspector.inspect(apk)
        assertEquals("54da2d6e1de01e30847e5a1c855221cb8a72fff2be111ea695488c855f0b1ae3", identity.fingerprint)
        assertNull(identity.reason)
    }

    @Test fun `apk without v1 certificate blocks records an honest v2-only reason`() {
        val identity = ApkPublisherInspector.inspect(BackupTestFixtures.apkWithSingleDex("eu.kanade.test"))
        assertNull(identity.fingerprint)
        assertEquals("v2-only (not verifiable in-process)", identity.reason)
    }

    @Test fun `garbage certificate block fails closed instead of guessing`() {
        val apk = BackupTestFixtures.apk(
            dexEntries = emptyMap(),
            extras = mapOf("META-INF/CERT.RSA" to "not a pkcs7 block".toByteArray()),
        )
        val identity = ApkPublisherInspector.inspect(apk)
        assertNull(identity.fingerprint)
        assertEquals("unreadable v1 certificate", identity.reason)
    }

    @Test fun `converter stores the fingerprint in the schema v2 manifest`() = runTest {
        val layout = SandboxLayout(temporary.newFolder().toPath())
        val descriptor = ExtensionDescriptor("eu.kanade.test", "Test", "tr", false, "2", 2,
            "https://example.org/test.apk", emptyList())
        val apk = BackupTestFixtures.apk(
            dexEntries = mapOf("classes.dex" to BackupTestFixtures.dex(strings = listOf("Leu/kanade/test/TestExtension;"))),
            extras = mapOf(
                "AndroidManifest.xml" to """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="eu.kanade.test" android:versionCode="2"/>""".toByteArray(),
                "META-INF/FIXTURE.RSA" to signatureBlock,
            ),
        )
        val manifest = RuntimeApkToJsonConverter(ApkSandboxExtractor(layout)).convert(apk, descriptor, "https://example.org/index.json", 4)
        assertEquals("54da2d6e1de01e30847e5a1c855221cb8a72fff2be111ea695488c855f0b1ae3", manifest.publisherFingerprint)
        assertNull(manifest.publisherFingerprintReason)
        // The manifest, fingerprint included, survives the JSON contract unchanged.
        assertEquals(manifest, JsonSourceManifestCodec.decode(JsonSourceManifestCodec.encode(manifest)))
    }
}
