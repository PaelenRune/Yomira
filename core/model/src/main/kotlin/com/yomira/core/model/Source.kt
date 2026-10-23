package com.yomira.core.model

data class Source(
    val id: String,
    val name: String,
    val baseUrl: String,
    val lang: String,
    val isNsfw: Boolean = false,
    val iconUrl: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Source ID must not be blank" }
        require(name.isNotBlank()) { "Source name must not be blank" }
        require(baseUrl.isNotBlank()) { "Source base URL must not be blank" }
        require(lang.isNotBlank()) { "Source language must not be blank" }
    }
}
