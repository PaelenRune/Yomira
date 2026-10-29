package com.yomira.data.metadata

/**
 * One alternative name for a series, as a metadata provider reported it.
 *
 * [seriesId] is the waterfall identity (`sourceId:remoteId`) of the merged
 * record the alias belongs to; [lang] is the provider-supplied language code
 * (`en`, `ja`, `en-jp`, …) or null when the provider did not classify the
 * title; [source] is the provider id that reported the alias.
 */
data class SeriesAlias(
    val seriesId: String,
    val title: String,
    val lang: String? = null,
    val source: String,
)

/**
 * Write path for the series alias dictionary.
 *
 * The waterfall aggregator feeds every title it observes — canonical,
 * English and all localized alt titles — into this sink so that later lookups
 * can resolve a series through any of its names. The sink is write-only
 * evidence for *matching*; display-title selection stays the exclusive job of
 * [TitleLocalizer] and nothing read from this dictionary may ever change what
 * the shelf shows.
 */
fun interface SeriesAliasSink {
    /**
     * Records [aliases] for their series. Implementations must be idempotent:
     * the aggregator re-records on every cache hit. A throwing implementation
     * must never break aggregation — the aggregator guards every call.
     */
    suspend fun record(aliases: List<SeriesAlias>)
}
