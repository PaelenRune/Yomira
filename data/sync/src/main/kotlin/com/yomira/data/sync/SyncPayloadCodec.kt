package com.yomira.data.sync

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Raised when a remote document is not a valid, supported Yomira sync payload. */
class SyncPayloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Canonical JSON codec for [SyncPayload].
 *
 * The format is deliberately small and forward compatible:
 * - unknown keys are ignored so a newer device can write fields an older app ignores;
 * - defaults are written so every document is self-describing;
 * - nulls are omitted to keep the Drive object tiny.
 */
class SyncPayloadCodec(
    private val json: Json = defaultJson,
) {
    fun encode(payload: SyncPayload): String {
        payload.validate()
        val encoded = json.encodeToString(payload.normalized())
        val size = encoded.encodeToByteArray().size
        if (size > MAX_PAYLOAD_BYTES) {
            throw SyncPayloadException(
                "Sync payload is too large: $size bytes (limit $MAX_PAYLOAD_BYTES)",
            )
        }
        return encoded
    }

    fun decode(raw: String): SyncPayload {
        if (raw.isBlank()) throw SyncPayloadException("Sync payload is empty")
        val bytes = raw.encodeToByteArray()
        if (bytes.size > MAX_PAYLOAD_BYTES) {
            throw SyncPayloadException(
                "Sync payload is too large: ${bytes.size} bytes (limit $MAX_PAYLOAD_BYTES)",
            )
        }
        val payload = try {
            json.decodeFromString<SyncPayload>(raw)
        } catch (error: SerializationException) {
            throw SyncPayloadException("Malformed sync payload", error)
        } catch (error: IllegalArgumentException) {
            throw SyncPayloadException("Invalid sync payload", error)
        }
        try {
            payload.validate()
        } catch (error: IllegalArgumentException) {
            throw SyncPayloadException("Invalid sync payload: ${error.message}", error)
        }
        return payload.normalized()
    }

    companion object {
        /** A hard cap keeps background sync cheap and protects the app from hostile files. */
        const val MAX_PAYLOAD_BYTES: Int = 1024 * 1024

        val defaultJson: Json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            isLenient = false
            allowStructuredMapKeys = false
        }
    }
}
