package com.yomira.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal val JsonMediaType = "application/json; charset=utf-8".toMediaType()

internal fun OkHttpClient.executeChecked(request: Request, sourceId: String): Response {
    val response = try {
        newCall(request).execute()
    } catch (error: IOException) {
        throw MetadataClientException(sourceId, message = "Transport failure", cause = error)
    }
    if (!response.isSuccessful) {
        val code = response.code
        response.close()
        throw MetadataClientException(sourceId, statusCode = code, message = "HTTP $code")
    }
    return response
}

internal inline fun <reified T> Response.decodeJson(json: Json, sourceId: String): T {
    val payload = try {
        body?.string().orEmpty()
    } catch (error: IOException) {
        throw MetadataClientException(sourceId, message = "Unable to read response body", cause = error)
    }
    return try {
        json.decodeFromString<T>(payload)
    } catch (error: SerializationException) {
        throw MetadataClientException(sourceId, message = "Malformed response payload", cause = error)
    }
}

internal fun Request.Builder.commonHeaders(userAgent: String): Request.Builder = apply {
    header("User-Agent", userAgent)
    header("Accept", "application/json")
}

internal fun String.asJsonBody(): okhttp3.RequestBody = toRequestBody(JsonMediaType)

/** Appends repeated query parameters such as `contentRating[]=safe`. */
internal fun HttpUrl.Builder.addAll(name: String, values: Iterable<String>): HttpUrl.Builder = apply {
    values.forEach { addQueryParameter(name, it) }
}
