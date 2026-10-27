package com.yomira.domain.extension

import java.util.Base64

/** Persistent routing separate from Room's globally unique chapter ID. */
data class SourceChapterLocation(val packageName: String, val sourceId: String, val chapterId: String) {
    fun encode(): String = PREFIX + listOf(packageName, sourceId, chapterId).joinToString("/") {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val PREFIX = "yomira-source:"

        fun decode(value: String): SourceChapterLocation? {
            if (!value.startsWith(PREFIX)) return null
            val parts = value.removePrefix(PREFIX).split('/')
            require(parts.size == 3) { "Invalid source chapter location" }
            val decoded = parts.map { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
            require(decoded.none { it.isBlank() }) { "Empty source chapter location" }
            return SourceChapterLocation(decoded[0], decoded[1], decoded[2])
        }
    }
}
