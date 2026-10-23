package com.yomira.core.network

import java.io.IOException

/**
 * Failure raised by a metadata client. The waterfall aggregator isolates these
 * failures so that one unavailable provider never blocks the whole merge.
 */
class MetadataClientException(
    val sourceId: String,
    val statusCode: Int? = null,
    message: String,
    cause: Throwable? = null,
) : IOException("[$sourceId] $message", cause)
