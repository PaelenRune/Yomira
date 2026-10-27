package com.yomira.data.extension

import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import com.yomira.domain.extension.requireHttps
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Use one store instance per private application file. No APKs or copyrighted content are stored. */
class FileRepositoryStore(
    path: Path,
    private val defaultRepositories: List<String> = EmbeddedRepositories.load().map { it.indexUrl },
) : RepositoryStore {
    private val file = AtomicJsonFile(path)

    override suspend fun read(): List<String> = file.read()?.let { text ->
        Json.parseToJsonElement(text).jsonArray.flatMap {
            val url = it.jsonPrimitive.content.also(::requireHttps)
            // Migrate the old auto-seeded overlapping indexes to the non-overlapping bundled catalog.
            if (url in LEGACY_BUNDLED_URLS) EmbeddedRepositories.load().map { entry -> entry.indexUrl }
            else listOf(url)
        }.distinct()
    } ?: defaultRepositories

    override suspend fun write(urls: List<String>) {
        urls.forEach(::requireHttps)
        file.write(JsonArray(urls.distinct().map(::JsonPrimitive)).toString())
    }

    private companion object {
        val LEGACY_BUNDLED_URLS = setOf(
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
            "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/turkish/index.min.json",
            "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/western/index.min.json",
        )
    }
}

class FileOnboardingStore(path: Path) : OnboardingStore {
    private val file = AtomicJsonFile(path)

    override suspend fun read(): OnboardingState? = file.read()?.let { text ->
        val value = Json.parseToJsonElement(text).jsonObject
        OnboardingState(
            preferences = ReaderPreferences(
                ReadingLanguage.valueOf(value.getValue("language").jsonPrimitive.content),
                value.getValue("allowAdultContent").jsonPrimitive.boolean,
            ),
            repositoryUrl = value.getValue("repositoryUrl").jsonPrimitive.contentOrNull?.also(::requireHttps),
            suggestedPackages = value.getValue("suggestedPackages").jsonArray.map { it.jsonPrimitive.content },
            completed = value.getValue("completed").jsonPrimitive.boolean,
        )
    }

    override suspend fun write(state: OnboardingState) {
        state.repositoryUrl?.let(::requireHttps)
        file.write(buildJsonObject {
            put("language", state.preferences.language.name)
            put("allowAdultContent", state.preferences.allowAdultContent)
            put("repositoryUrl", state.repositoryUrl?.let(::JsonPrimitive) ?: JsonNull)
            put("suggestedPackages", JsonArray(state.suggestedPackages.map(::JsonPrimitive)))
            put("completed", state.completed)
        }.toString())
    }
}

/** Shared by all file-backed stores in this module; replacement is atomic and failure-safe. */
internal class AtomicJsonFile(path: Path) {
    private val path = path.toAbsolutePath()
    private val mutex = Mutex()

    suspend fun read(): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (Files.exists(path)) String(Files.readAllBytes(path), Charsets.UTF_8) else null
        }
    }

    suspend fun write(text: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            Files.createDirectories(path.parent)
            val temporary = Files.createTempFile(path.parent, ".yomira-", ".json")
            try {
                java.io.FileOutputStream(temporary.toFile()).use { stream ->
                    stream.write(text.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                Unit
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}
