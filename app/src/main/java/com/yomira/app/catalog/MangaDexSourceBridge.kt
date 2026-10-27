package com.yomira.app.catalog

import com.yomira.domain.extension.*
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Safe native fallback for MangaDex. Other APK sources need an isolated runtime, not fake data. */
class MangaDexSourceBridge(
    private val client: OkHttpClient,
    private val extensions: ExtensionRepository,
    private val onboarding: OnboardingStore,
    private val apiUrl: String = "https://api.mangadex.org/",
) : SourceBridge {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun languages(packageName: String, sourceId: String): List<String> {
        val source = extensions.available().firstOrNull { it.packageName == packageName }
            ?.sources?.firstOrNull { it.id == sourceId && it.name.equals("MangaDex", true) }
        checkNotNull(source) { "This source requires an isolated extension runtime" }
        if (source.language in listOf("en", "tr")) return listOf(source.language)
        check(source.language == "all") { "Unsupported MangaDex language" }
        return when (onboarding.read()?.preferences?.language ?: ReadingLanguage.ENGLISH) {
            ReadingLanguage.TURKISH -> listOf("tr")
            ReadingLanguage.ENGLISH -> listOf("en")
            ReadingLanguage.BOTH -> listOf("tr", "en")
        }
    }

    override suspend fun getMangaList(packageName: String, sourceId: String, query: String, page: Int): List<SourceManga> {
        val languages = languages(packageName, sourceId)
        require(query.isNotBlank() && page > 0)
        val result = request("manga", buildList {
            add("title" to query)
            add("limit" to "100")
            add("offset" to ((page - 1) * 100).toString())
            add("contentRating[]" to "safe")
            add("contentRating[]" to "suggestive")
            languages.forEach { add("availableTranslatedLanguage[]" to it) }
        })
        return result.array("data").mapNotNull { item ->
            val manga = item.jsonObject
            val attributes = manga.getValue("attributes").jsonObject
            val titles = attributes.getValue("title").jsonObject.values.map { it.jsonPrimitive.content } +
                attributes.array("altTitles").flatMap { it.jsonObject.values.map { value -> value.jsonPrimitive.content } }
            val title = titles.firstOrNull { it.trim().equals(query.trim(), true) } ?: titles.firstOrNull() ?: return@mapNotNull null
            val id = manga.getValue("id").jsonPrimitive.content
            SourceManga(id, title, "https://mangadex.org/title/$id")
        }
    }

    override suspend fun getChapterList(packageName: String, sourceId: String, manga: SourceManga): List<SourceChapter> {
        val languages = languages(packageName, sourceId)
        requireId(manga.id)
        val chapters = mutableListOf<SourceChapter>()
        var offset = 0
        do {
            val result = request("manga/${manga.id}/feed", buildList {
                add("limit" to "500")
                add("offset" to offset.toString())
                add("order[chapter]" to "asc")
                add("includeExternalUrl" to "0")
                add("contentRating[]" to "safe")
                add("contentRating[]" to "suggestive")
                languages.forEach { add("translatedLanguage[]" to it) }
            })
            val data = result.array("data")
            data.forEach { item ->
                val chapter = item.jsonObject
                val attributes = chapter.getValue("attributes").jsonObject
                if ((attributes["pages"]?.jsonPrimitive?.intOrNull ?: 0) <= 0) return@forEach
                if (attributes["translatedLanguage"]?.jsonPrimitive?.content !in languages) return@forEach
                val number = attributes["chapter"]?.jsonPrimitive?.contentOrNull
                val title = attributes["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                chapters += SourceChapter(
                    chapter.getValue("id").jsonPrimitive.content,
                    title ?: number?.let { "Chapter $it" }.orEmpty(),
                    number?.toDoubleOrNull(),
                    attributes["publishAt"]?.jsonPrimitive?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                )
            }
            offset += data.size
        } while (data.isNotEmpty() && offset < (result["total"]?.jsonPrimitive?.intOrNull ?: offset) && offset < 10_000)
        return chapters.distinctBy { it.id }
    }

    override suspend fun getPageList(packageName: String, sourceId: String, chapter: SourceChapter): List<SourcePage> {
        languages(packageName, sourceId)
        requireId(chapter.id)
        val result = request("at-home/server/${chapter.id}")
        val base = result.getValue("baseUrl").jsonPrimitive.content
        requireHttps(base)
        val data = result.getValue("chapter").jsonObject
        val hash = data.getValue("hash").jsonPrimitive.content
        require(hash.matches(Regex("[a-fA-F0-9]+")))
        return data.array("data").mapIndexed { index, file ->
            val url = base.toHttpUrl().newBuilder().addPathSegment("data").addPathSegment(hash)
                .addPathSegment(file.jsonPrimitive.content).build().toString()
            SourcePage(index, url)
        }
    }

    private fun requireId(id: String) {
        require(id.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")))
    }

    private suspend fun request(path: String, parameters: List<Pair<String, String>> = emptyList()): JsonObject {
        val url = apiUrl.toHttpUrl().newBuilder().addPathSegments(path)
            .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Yomira/5.4").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            check(it.isSuccessful) { "MangaDex HTTP ${it.code}" }
                            json.parseToJsonElement(checkNotNull(it.body).string()).jsonObject
                        }
                        continuation.resume(body)
                    } catch (error: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
}
