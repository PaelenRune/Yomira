package com.yomira.data.sync

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Minimal Google Drive REST v3 client for the hidden `appDataFolder`.
 *
 * Why appDataFolder:
 * - the file never appears in the user's My Drive or file picker;
 * - the app only needs the `https://www.googleapis.com/auth/drive.appdata` scope;
 * - the document is a few kilobytes of JSON, so it does not consume user media quota.
 *
 * The client never logs, stores or refreshes tokens. Authentication is delegated to
 * [DriveAccessTokenProvider], which is expected to return a short-lived OAuth token
 * obtained through Google Identity Services.
 */
class GoogleDriveClient(
    httpClient: OkHttpClient,
    private val tokenProvider: DriveAccessTokenProvider,
    private val codec: SyncPayloadCodec = SyncPayloadCodec(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SyncRemoteStore {
    private val client = httpClient.newBuilder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(): RemoteSyncSnapshot? {
        val token = requireToken()
        val files = findSyncFiles(token)
        if (files.isEmpty()) return null
        val snapshots = files.map { download(token, it.id) }
        return snapshots.first().copy(additionalPayloads = snapshots.drop(1).map { it.payload })
    }

    override suspend fun push(
        payload: SyncPayload,
        expected: RemoteSyncRevision?,
    ): RemoteSyncSnapshot {
        val body = codec.encode(payload)
        val token = requireToken()

        if (expected != null) {
            return try {
                update(token, expected, body, payload)
            } catch (error: SyncApiException) {
                // Re-fetch before recreating: another device may already have replaced the file.
                if (error.statusCode == HTTP_NOT_FOUND) {
                    throw SyncWriteConflictException()
                } else {
                    throw error
                }
            }
        }

        // fetch() returned null, but another device may have created the file since.
        val existing = findSyncFiles(token).firstOrNull()
        return if (existing == null) {
            create(token, body, payload)
        } else {
            // Never overwrite a document we haven't downloaded and merged.
            throw SyncWriteConflictException()
        }
    }

    private suspend fun findSyncFiles(token: String): List<DriveFile> {
        val url = "$DRIVE_API_BASE/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", APPDATA_SPACE)
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime)")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "100")
            .build()
        val request = authorized(token).url(url).get().build()
        return execute(request).use { response ->
            ensureSuccess(response, "list sync file")
            val body = readBody(response)
            val list = try {
                json.decodeFromString<DriveFileList>(body)
            } catch (error: IllegalArgumentException) {
                throw SyncTransportException("Drive returned an invalid file list", error)
            }
            if (list.nextPageToken != null) throw SyncTransportException("Too many duplicate sync documents to merge safely")
            list.files
        }
    }

    private suspend fun download(token: String, fileId: String): RemoteSyncSnapshot {
        val url = "$DRIVE_API_BASE/files".toHttpUrl().newBuilder()
            .addPathSegment(fileId)
            .addQueryParameter("alt", "media")
            .build()
        val request = authorized(token).url(url).get().build()
        return execute(request).use { response ->
            ensureSuccess(response, "download sync file")
            val etag = response.header("ETag")
                ?: throw SyncTransportException("Drive did not return a revision ETag")
            RemoteSyncSnapshot(codec.decode(readBody(response)), RemoteSyncRevision(fileId, etag))
        }
    }

    private suspend fun create(
        token: String,
        content: String,
        payload: SyncPayload,
    ): RemoteSyncSnapshot {
        val metadata = """{"name":"$FILE_NAME","parents":["$APPDATA_SPACE"]}"""
        val body = multipart(metadata, content)
        val url = "$DRIVE_UPLOAD_BASE/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", RESPONSE_FIELDS)
            .build()
        val request = authorized(token)
            .url(url)
            .post(body)
            .build()
        return execute(request).use { response ->
            ensureSuccess(response, "create sync file")
            val file = decodeFile(response)
            RemoteSyncSnapshot(payload, RemoteSyncRevision(file.id, response.header("ETag")))
        }
    }

    private suspend fun update(
        token: String,
        expected: RemoteSyncRevision,
        content: String,
        payload: SyncPayload,
    ): RemoteSyncSnapshot {
        val etag = expected.etag ?: throw SyncWriteConflictException()
        val metadata = """{"name":"$FILE_NAME"}"""
        val body = multipart(metadata, content)
        val url = "$DRIVE_UPLOAD_BASE/files".toHttpUrl().newBuilder()
            .addPathSegment(expected.fileId)
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", RESPONSE_FIELDS)
            .build()
        val request = authorized(token)
            .url(url)
            .patch(body)
            .header("If-Match", etag)
            .build()
        return execute(request).use { response ->
            when (response.code) {
                HTTP_PRECONDITION_FAILED -> throw SyncWriteConflictException()
                HTTP_NOT_FOUND -> throw SyncApiException(
                    response.code,
                    "The remote sync file no longer exists",
                )
            }
            ensureSuccess(response, "update sync file")
            val file = decodeFile(response)
            RemoteSyncSnapshot(payload, RemoteSyncRevision(file.id, response.header("ETag")))
        }
    }

    private fun multipart(metadata: String, content: String): MultipartBody =
        MultipartBody.Builder("yomira-${UUID.randomUUID()}")
            .setType(MULTIPART_RELATED)
            .addPart(metadata.toRequestBody(JSON_MEDIA_TYPE))
            .addPart(content.toRequestBody(JSON_MEDIA_TYPE))
            .build()

    private fun decodeFile(response: Response): DriveFile {
        val body = readBody(response)
        return try {
            json.decodeFromString<DriveFile>(body)
        } catch (error: IllegalArgumentException) {
            throw SyncTransportException("Drive returned an invalid file descriptor", error)
        }
    }

    private fun authorized(token: String): Request.Builder =
        Request.Builder().header("Authorization", "Bearer $token")

    private suspend fun requireToken(): String =
        tokenProvider.accessToken()?.trim()?.takeIf(String::isNotEmpty)
            ?: throw SyncNotAuthenticatedException()

    private fun ensureSuccess(response: Response, operation: String) {
        if (response.isSuccessful) return
        val message = "$operation failed with HTTP ${response.code}"
        when (response.code) {
            HTTP_UNAUTHORIZED -> throw SyncNotAuthenticatedException(
                "Google Drive authorization expired",
            )
            HTTP_NOT_FOUND, HTTP_PRECONDITION_FAILED -> throw SyncApiException(response.code, message)
            else -> throw SyncApiException(response.code, message)
        }
    }

    /** Responsible for closing [response]; callers use it inside `use`. */
    private fun readBody(response: Response): String {
        val body = response.body
        if (body.contentLength() > MAX_RESPONSE_BYTES) {
            throw SyncTransportException("Drive response is too large")
        }
        val source = body.source()
        if (source.request(MAX_RESPONSE_BYTES + 1)) {
            throw SyncTransportException("Drive response is too large")
        }
        return source.readUtf8()
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    private companion object {
        const val FILE_NAME = "yomira_sync.json"
        const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        const val APPDATA_SPACE = "appDataFolder"
        const val RESPONSE_FIELDS = "id,name,modifiedTime"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_PRECONDITION_FAILED = 412

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val MULTIPART_RELATED = "multipart/related".toMediaType()

        /** The sync document must stay tiny; this also bounds hostile Drive files. */
        const val MAX_RESPONSE_BYTES = 1024L * 1024L
    }
}

@Serializable
private data class DriveFile(
    val id: String,
    val name: String? = null,
    val modifiedTime: String? = null,
)

@Serializable
private data class DriveFileList(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String? = null,
)
