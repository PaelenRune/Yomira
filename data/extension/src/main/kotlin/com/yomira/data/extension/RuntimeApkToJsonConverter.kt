package com.yomira.data.extension

import com.yomira.domain.extension.ExtensionDescriptor

/** APK transport -> standard JSON definition, with compiled behavior retained as hash-pinned DEX. */
class RuntimeApkToJsonConverter(private val extractor: ApkSandboxExtractor) {
    suspend fun convert(
        apkBytes: ByteArray,
        descriptor: ExtensionDescriptor,
        repositoryUrl: String,
        installedAt: Long,
    ): JsonSourceManifest {
        com.yomira.domain.extension.requireHttps(repositoryUrl)
        require(installedAt >= 0)
        val publisher = ApkPublisherInspector.inspect(apkBytes)
        val extraction = extractor.extract(apkBytes, descriptor.packageName, installedAt, descriptor.versionCode)
        return JsonSourceManifest.of(
            descriptor, repositoryUrl, extraction.installedAt,
            extraction.dexFiles, extraction.entryClassCandidates,
        ).copy(apkMetadata = extraction.apkMetadata, publisherFingerprint = publisher.fingerprint,
            publisherFingerprintReason = publisher.reason).also { it.validate() }
    }
}
