package com.yomira.data.extension

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-private on-disk layout for JSON-first extensions. Everything lives under one directory
 * the app owns (`context.getDir("extension_sandbox", MODE_PRIVATE)` on Android), so:
 *
 * - nothing is ever registered with the Android package manager — no `pm install`, no
 *   "install unknown apps" warning, no launcher-drawer pollution;
 * - only [SandboxLayout.manifestDirectory] content is Drive-sync material; dex payloads are
 *   machine-local caches that can be thrown away and re-downloaded via the manifest `apkUrl`.
 *
 * Layout:
 * ```text
 * sandbox/
 *   dex/<safe-package>/classes.dex [classes2.dex …]
 *   manifests/<safe-package>.json      one JsonSourceManifest per extension
 *   sandbox_index.json                 optional compact Drive export of every manifest
 * ```
 */
class SandboxLayout(val root: Path) {
    private val extractionMutex = Mutex()

    /** Coordinates staging with cache inspection/cleanup without touching installed dex. */
    suspend fun <T> withExtractionLock(block: suspend () -> T): T = extractionMutex.withLock { block() }

    val dexRoot: Path = root.resolve("dex")
    val manifestDirectory: Path = root.resolve("manifests")
    val indexFile: Path = root.resolve("sandbox_index.json")

    /** Folder/file names are derived from the package name, never from it verbatim. */
    fun safeName(packageName: String): String {
        require(packageName.isNotBlank()) { "Package name must not be blank" }
        val safe = packageName.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
        require(safe.isNotBlank() && safe != "." && safe != "..") { "Package name escapes the sandbox: $packageName" }
        return safe
    }

    fun dexDirectory(packageName: String): Path = dexRoot.resolve(safeName(packageName))

    fun manifestFile(packageName: String): Path = manifestDirectory.resolve("${safeName(packageName)}.json")

    /** Removes every extracted dex for a package; manifests are owned by the store. */
    suspend fun deleteDex(packageName: String) {
        Files.createDirectories(dexRoot)
        deleteRecursively(dexDirectory(packageName))
    }

    private suspend fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { entry ->
                try {
                    Files.deleteIfExists(entry)
                } catch (error: IOException) {
                    throw IOException("Could not clean sandbox path ${entry.fileName}", error)
                }
            }
        }
    }
}

/**
 * Turns a downloaded repository APK into sandbox material **without touching the Android
 * package manager**. The archive is only ever read as a zip in-process: every `classes*.dex`
 * entry is validated, hashed and written into the app-private [SandboxLayout], and the
 * remaining metadata (scraping rules are compiled dex code plus source ids) is captured by
 * [JsonSourceManifest.of].
 *
 * Text/binary Android manifests supply package identity and declared entry classes. Assets are
 * fingerprinted, and an optional assets/yomira/extension.json supplies native entry metadata.
 * This is not APK signature verification or a translation of arbitrary bytecode into scraping rules.
 */
class ApkSandboxExtractor(
    private val layout: SandboxLayout,
    private val maxDexBytesPerFile: Long = DEFAULT_MAX_DEX_BYTES,
    private val maxTotalDexBytes: Long = DEFAULT_MAX_TOTAL_DEX_BYTES,
) {
    init {
        require(maxDexBytesPerFile > 0 && maxTotalDexBytes >= maxDexBytesPerFile) { "Invalid dex size limits" }
    }

    suspend fun extract(apkBytes: ByteArray, packageName: String, installedAt: Long, expectedVersionCode: Long? = null): SandboxExtraction = layout.withExtractionLock {
        require(apkBytes.isNotEmpty()) { "Empty APK payload" }
        val target = layout.dexDirectory(packageName)
        Files.createDirectories(layout.dexRoot)

        val extracted = mutableListOf<SandboxedDexFile>()
        val candidateClasses = sortedSetOf<String>()
        var metadata = ApkMetadata()
        val assets = mutableListOf<ApkAsset>()
        val seenNames = mutableSetOf<String>()
        val temporary = Files.createTempDirectory(layout.dexRoot, ".extracting-")
        try {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(apkBytes)).use { archive ->
                var total = 0L
                var seenAny = false
                while (true) {
                    val entry = archive.nextEntry ?: break
                    seenAny = true
                    require(seenNames.add(entry.name) && seenNames.size <= 4096) { "Duplicate or excessive APK entries" }
                    val isDex = entry.isDexEntry() // Validates paths for every entry, not only DEX.
                    if (entry.name == "AndroidManifest.xml" || entry.name.startsWith("assets/") && !entry.isDirectory) {
                        val bytes = readCapped(archive, 4L * 1024 * 1024)
                        total += bytes.size
                        require(total <= maxTotalDexBytes) { "APK payload exceeds sandbox quota" }
                        if (entry.name == "AndroidManifest.xml") {
                            metadata = ApkMetadataInspector.manifest(bytes)
                            require(metadata.packageName == packageName) { "APK manifest package mismatch" }
                            require(expectedVersionCode == null || metadata.versionCode == null || metadata.versionCode == expectedVersionCode) {
                                "APK manifest version mismatch"
                            }
                            candidateClasses += metadata.entryClasses
                        } else {
                            assets += ApkAsset(entry.name, bytes.size.toLong(), sha256Hex(bytes))
                            if (entry.name == "assets/yomira/extension.json") {
                                candidateClasses += ApkMetadataInspector.assetClasses(bytes, packageName)
                            }
                        }
                        continue
                    }
                    if (!isDex) {
                        // Even skipped ZIP entries must be bounded: nextEntry otherwise inflates them implicitly.
                        if (!entry.isDirectory) {
                            total += readCapped(archive, maxDexBytesPerFile).size
                            require(total <= maxTotalDexBytes) { "APK payload exceeds sandbox quota" }
                        }
                        continue
                    }
                    val name = entry.name.substringAfterLast('/').lowercase()
                    require(name == DEX_NAME || Regex("""classes\d+\.dex""").matches(name)) { "Unsafe dex name ${entry.name}" }
                    val bytes = readCapped(archive, maxDexBytesPerFile)
                    total += bytes.size
                    require(total <= maxTotalDexBytes) { "Dex payload exceeds sandbox quota" }
                    val inspected = DexHeader.inspect(bytes, entry.name)
                    candidateClasses += inspected.entryClassCandidates
                    val staged = temporary.resolve(name)
                    Files.write(staged, bytes)
                    extracted += SandboxedDexFile(name, bytes.size.toLong(), sha256Hex(bytes))
                }
                require(seenAny) { "Archive is not a valid zip payload" }
            }
            require(extracted.isNotEmpty()) { "Archive contains no classes*.dex" }
            // Validate the entire archive before replacing an existing working payload.
            layout.deleteDex(packageName)
            Files.createDirectories(target)
            extracted.forEach { Files.move(temporary.resolve(it.name), target.resolve(it.name), StandardCopyOption.REPLACE_EXISTING) }
            return@withExtractionLock SandboxExtraction(
                dexFiles = extracted.sortedBy { it.name },
                entryClassCandidates = candidateClasses.toList(),
                installedAt = installedAt,
                sandboxDirectory = target,
                apkMetadata = metadata.copy(assets = assets.sortedBy { it.name }),
            )
        } finally {
            deleteTree(temporary)
        }
    }

    private fun java.util.zip.ZipEntry.isDexEntry(): Boolean {
        val name = this.name
        if (name.contains("..") || name.startsWith("/") || name.contains("\\")) {
            throw IOException("Unsafe archive entry: $name")
        }
        return !isDirectory && name.matches(Regex("""classes\d*\.dex"""))
    }

    private fun readCapped(stream: java.io.InputStream, cap: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var read = 0L
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            read += count
            if (read > cap) throw IOException("Dex exceeds size limit of $cap bytes")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun deleteTree(directory: Path) {
        if (!Files.exists(directory)) return
        runCatching {
            Files.walk(directory).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_DEX_BYTES = 64L * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_DEX_BYTES = 192L * 1024 * 1024
        private const val DEX_NAME = "classes.dex"

        fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

/** Result of one successful sandbox extraction; feeds [JsonSourceManifest.of]. */
data class SandboxExtraction(
    val dexFiles: List<SandboxedDexFile>,
    val entryClassCandidates: List<String>,
    val installedAt: Long,
    val sandboxDirectory: Path,
    val apkMetadata: ApkMetadata = ApkMetadata(),
)

/**
 * Minimal DEX container header validation plus entry-class discovery.
 *
 * Parses only what is safe and cheap: magic/version, endianness tag, sizes and the
 * `string_ids` table. Class descriptors that end in `Extension` are the stable naming
 * convention every Mihon/Keiyoushi extension follows (`<Package>Extension`), which is how a
 * `DexClassLoader` host finds the entry point without trusting the archive at large.
 */
data class DexHeader(
    val version: String,
    val entryClassCandidates: List<String>,
) {
    companion object {
        private val SUPPORTED_VERSIONS = setOf("035", "036", "037", "038", "039", "040", "A00")
        private const val ENDIAN_TAG = 0x12345678
        private const val HEADER_SIZE = 0x70
        const val MAX_CLASS_CANDIDATES = 24

        /** @throws IllegalArgumentException when the bytes are not a little-endian DEX file. */
        fun inspect(bytes: ByteArray, sourceName: String = "classes.dex"): DexHeader {
            fun fail(reason: String): Nothing = throw IllegalArgumentException("Invalid dex $sourceName: $reason")
            if (bytes.size < HEADER_SIZE) fail("shorter than ${HEADER_SIZE}-byte header")
            if (!(bytes[0] == 'd'.code.toByte() && bytes[1] == 'e'.code.toByte() && bytes[2] == 'x'.code.toByte() && bytes[3] == '\n'.code.toByte())) {
                fail("missing dex magic")
            }
            val version = String(bytes, 4, 3, Charsets.US_ASCII)
            if (version !in SUPPORTED_VERSIONS || bytes[7] != 0.toByte()) fail("unsupported version \"$version\"")
            val littleEndian = readIntLE(bytes, 40)
            if (littleEndian != ENDIAN_TAG) fail("big-endian or corrupt endian tag")
            val fileSize = readIntLE(bytes, 32)
            require(fileSize in 0 until bytes.size + 1) { "Invalid dex $sourceName: file size $fileSize" }
            if (fileSize != 0 && fileSize != bytes.size) fail("declared size $fileSize != actual ${bytes.size}")

            val stringIdsSize = readIntLE(bytes, 56)
            val stringIdsOffset = readIntLE(bytes, 60)
            if (stringIdsSize <= 0 || stringIdsOffset <= 0 || stringIdsOffset.toLong() + stringIdsSize.toLong() * 4L > bytes.size) {
                // A header without a readable string table still extracts fine; the class
                // scan is an optimisation, not a security boundary.
                return DexHeader(version, emptyList())
            }
            val candidates = sortedSetOf<String>()
            for (index in 0 until stringIdsSize) {
                val offset = readIntLE(bytes, stringIdsOffset + index * 4).toLong()
                val descriptor = readStringTableEntry(bytes, offset) ?: continue
                val className = if (descriptor.startsWith('L') && descriptor.endsWith(';')) descriptor.substring(1, descriptor.length - 1) else descriptor
                if (className.endsWith(CLASS_SUFFIX) && isClassDescriptor(className) && className.length <= 256) {
                    candidates += className
                    if (candidates.size > MAX_CLASS_CANDIDATES) candidates.remove(candidates.last())
                }
            }
            return DexHeader(version, candidates.toList())
        }

        private fun readIntLE(bytes: ByteArray, offset: Int): Int {
            if (offset < 0 || offset + 4 > bytes.size) throw IllegalArgumentException("Invalid dex: offset $offset out of bounds")
            return bytes[offset].toInt() and 0xFF or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        }

        /** Decodes one `uleb128 length + MUTF-8` entry; returns null on malformed data. */
        private fun readStringTableEntry(bytes: ByteArray, offset: Long): String? {
            if (offset < 0 || offset >= bytes.size) return null
            var position = offset.toInt()
            var length = 0
            var shift = 0
            while (position < bytes.size) {
                val byte = bytes[position].toInt() and 0xFF
                position++
                length = length or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
                if (shift > 28 || position >= bytes.size) return null
            }
            if (length < 0 || position + length > bytes.size) return null
            // Class descriptors are pure ASCII; anything else (UI strings, selectors) is skipped.
            var ascii = true
            for (index in position until position + length) {
                val byte = bytes[index].toInt() and 0xFF
                if (byte == 0 || byte >= 0x80) { ascii = false; break }
            }
            if (!ascii) return null
            return String(bytes, position, length, Charsets.US_ASCII)
        }

        private const val CLASS_SUFFIX = "Extension"

        private fun isClassDescriptor(value: String): Boolean {
            if (!value.contains('/')) return false
            return value.all { it.isLetterOrDigit() || it == '/' || it == '_' || it == '$' } &&
                value.first().isLetter() && value.last() != '/'
        }
    }
}
