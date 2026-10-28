package com.yomira.data.extension

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipInputStream

/** Certificate identity evidence, not a CA trust decision or APK signature verification. */
data class ApkPublisherIdentity(val fingerprint: String?, val reason: String?)

object ApkPublisherInspector {
    fun inspect(apk: ByteArray): ApkPublisherIdentity {
        val fingerprints = mutableSetOf<String>()
        var found = false
        try {
            ZipInputStream(ByteArrayInputStream(apk)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && Regex("META-INF/[^/]+\\.(RSA|DSA|EC)", RegexOption.IGNORE_CASE).matches(entry.name)) {
                        found = true
                        val block = zip.readNBytes(MAX_CERTIFICATE_BLOCK + 1)
                        require(block.size <= MAX_CERTIFICATE_BLOCK)
                        fingerprints += parseSignatureBlock(block)
                    }
                }
            }
        } catch (_: Exception) {
            return ApkPublisherIdentity(null, "unreadable v1 certificate")
        }
        return when {
            !found -> ApkPublisherIdentity(null, "v2-only (not verifiable in-process)")
            fingerprints.size != 1 -> ApkPublisherIdentity(null, "ambiguous v1 certificates")
            else -> ApkPublisherIdentity(fingerprints.single(), null)
        }
    }

    internal fun parseSignatureBlock(block: ByteArray): List<String> {
        val certificates = CertificateFactory.getInstance("X.509")
            .generateCertificates(ByteArrayInputStream(block)).filterIsInstance<X509Certificate>()
        // Do not accidentally display a chain's CA certificate as the publisher.
        val leaves = certificates.filter { it.basicConstraints < 0 }
        return (if (certificates.size == 1) certificates else leaves)
            .map { ApkSandboxExtractor.sha256Hex(it.encoded) }.distinct()
    }

    private const val MAX_CERTIFICATE_BLOCK = 1024 * 1024
}
