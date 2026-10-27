package com.yomira.data.extension

import java.util.Base64

/** Casual-inspection obfuscation, not a secrecy boundary: the decoding key ships in the app. */
internal object RepositoryPayload {
    private const val PREFIX = "YOMIRA-XOR-1:"
    private val key = "yomira-repository-v1".toByteArray(Charsets.UTF_8)

    fun decode(payload: String, encrypted: Boolean = false): String {
        val value = payload.trim()
        if (!value.startsWith(PREFIX)) {
            require(!encrypted) { "Missing repository encoding header" }
            return payload
        }
        val bytes = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        return String(ByteArray(bytes.size) { index ->
            (bytes[index].toInt() xor key[index % key.size].toInt()).toByte()
        }, Charsets.UTF_8)
    }
}
