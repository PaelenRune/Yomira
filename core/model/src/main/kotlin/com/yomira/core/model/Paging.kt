package com.yomira.core.model

/**
 * One page of an offset/limit catalogue window.
 *
 * Both AniList (`page`, 1-based) and MangaDex / ComicVine (`offset`, 0-based)
 * are derived from the same value object so the UI, the network clients and the
 * unit tests can never disagree about where page 2 starts.
 */
data class PageWindow(
    val page: Int,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(page >= 1) { "Page numbers are 1-based" }
        require(pageSize in 1..MAX_PAGE_SIZE) { "Page size must be between 1 and $MAX_PAGE_SIZE" }
    }

    /** Zero-based index of the first record, for REST providers that paginate by `offset`. */
    val offset: Int get() = (page - 1) * pageSize

    /** The 1-based page that contains [offset]; used to resume a feed from Room. */
    fun next(): PageWindow = copy(page = page + 1)

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val MAX_PAGE_SIZE = 50

        fun fromOffset(offset: Int, pageSize: Int = DEFAULT_PAGE_SIZE): PageWindow {
            require(offset >= 0) { "Offset must not be negative" }
            require(pageSize in 1..MAX_PAGE_SIZE) { "Page size must be between 1 and $MAX_PAGE_SIZE" }
            return PageWindow(page = offset / pageSize + 1, pageSize = pageSize)
        }
    }
}

/** A fetched page plus the provider's own "there is more" signal. */
data class PageSlice<T>(
    val items: List<T>,
    val hasMore: Boolean,
)
