package com.yomira.app.extensions

/**
 * Logo attempt state for [ExtensionLogoImage].
 *
 * The initials fallback must stay visible whenever no candidate has succeeded:
 * while the first request is still pending, after every failure, and permanently
 * once every candidate URL is exhausted. Pure state: the Compose layer feeds
 * Coil's `onSuccess`/`onError` callbacks in, unit tests drive the error path
 * with no loader at all.
 */
internal data class ExtensionLogoFallback(
    val urls: List<String>,
    val attempt: Int = 0,
    val loaded: Boolean = false,
) {
    /** The candidate to request next, or null when every candidate has failed. */
    val requestUrl: String? get() = urls.getOrNull(attempt)

    /** Initials on the OLED plate are the default state, not an afterthought. */
    val showInitials: Boolean get() = !loaded

    fun onImageSuccess(): ExtensionLogoFallback = copy(loaded = true)

    /**
     * Advance to the next candidate only while one was actually in flight.
     * Once loaded, a stray duplicated error must not tear down the visible
     * image; once exhausted, it must not overshoot past the list.
     */
    fun onImageError(): ExtensionLogoFallback = when {
        loaded -> this
        requestUrl != null -> copy(attempt = attempt + 1)
        else -> this
    }
}
