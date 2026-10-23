package com.yomira.core.network

import kotlinx.serialization.json.Json

/** Shared, tolerant JSON reader for heterogeneous upstream catalogue payloads. */
object NetworkJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }
}
