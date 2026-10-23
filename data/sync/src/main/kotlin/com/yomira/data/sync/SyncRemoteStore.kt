package com.yomira.data.sync

import java.io.IOException

/**
 * Opaque Drive revision used for optimistic concurrency. A revision identifies the
 * exact remote file (id + ETag); it is passed back to [SyncRemoteStore.push] so a
 * concurrent device cannot be silently overwritten.
 */
data class RemoteSyncRevision(
    val fileId: String,
    val etag: String? = null,
)

data class RemoteSyncSnapshot(
    val payload: SyncPayload,
    val revision: RemoteSyncRevision,
)

/**
 * Transport port for the Yomira sync document. [GoogleDriveClient] is the production
 * implementation; tests can supply an in-memory fake without touching the network.
 */
interface SyncRemoteStore {
    /** Returns null when the user has not uploaded a document yet. */
    suspend fun fetch(): RemoteSyncSnapshot?

    /**
     * Creates or replaces the remote document.
     *
     * @param expected revision observed by [fetch], or null for a brand-new document.
     * @throws SyncWriteConflictException when another device wrote in the meantime.
     */
    suspend fun push(payload: SyncPayload, expected: RemoteSyncRevision?): RemoteSyncSnapshot
}

/** Supply a short-lived OAuth 2.0 access token for the drive.appdata scope. */
fun interface DriveAccessTokenProvider {
    /** Return null when the user is not signed in. Never log or persist the token. */
    suspend fun accessToken(): String?
}

open class SyncTransportException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** The user has not connected Google Drive (or the token was revoked). */
class SyncNotAuthenticatedException(
    message: String = "Google Drive is not connected",
) : SyncTransportException(message)

/** Another client changed the remote document between fetch and push. */
class SyncWriteConflictException(
    message: String = "The remote sync document changed concurrently",
) : SyncTransportException(message)

/** A non-successful Drive HTTP response. */
class SyncApiException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : SyncTransportException(message, cause) {
    /** 408/429/5xx are transient; 4xx (except 408/429) need user or data intervention. */
    val retryable: Boolean
        get() = statusCode == 408 || statusCode == 429 || statusCode >= 500
}
