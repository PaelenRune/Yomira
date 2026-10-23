package com.yomira.core.network

import kotlinx.coroutines.CancellationException

/**
 * One upstream metadata catalogue. Implementations must be safe to call from
 * any dispatcher and must never leak unchecked exceptions for transport errors;
 * [MetadataClientException] is the expected failure type.
 */
interface MetadataProvider {
    val sourceId: String

    /** Providers without credentials (for example ComicVine) can opt out. */
    val isAvailable: Boolean get() = true

    suspend fun search(query: String, limit: Int = 5): List<RemoteMetadata>

    /** Optional direct lookup used by refresh flows. */
    suspend fun findById(remoteId: String): RemoteMetadata? = null
}

/** Guarded provider call: one broken source must not break the cascade. */
internal suspend fun MetadataProvider.safeSearch(
    query: String,
    limit: Int,
    onFailure: (ProviderFailure) -> Unit,
): List<RemoteMetadata> {
    if (!isAvailable) return emptyList()
    return try {
        search(query, limit).filter { it.title.isNotBlank() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onFailure(ProviderFailure(sourceId, error.message ?: error::class.simpleName.orEmpty()))
        emptyList()
    }
}

data class ProviderFailure(val sourceId: String, val message: String)
