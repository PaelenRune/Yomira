package com.yomira.data.extension

import com.yomira.domain.extension.FavoriteSeries
import kotlinx.serialization.json.*

/** Shared encoding for file and Android preferences; absent v1 values are empty. */
object FavoriteSeriesJson {
    fun encode(series: List<FavoriteSeries>): String = JsonArray(series.map { item ->
        buildJsonObject {
            put("catalogId", item.catalogId?.let(::JsonPrimitive) ?: JsonNull)
            put("title", item.title)
        }
    }).toString()

    fun decode(value: String?): List<FavoriteSeries> = value?.let { text ->
        Json.parseToJsonElement(text).jsonArray.map { element ->
            val item = element.jsonObject
            FavoriteSeries(item["catalogId"]?.jsonPrimitive?.contentOrNull, item.getValue("title").jsonPrimitive.content)
        }
    }.orEmpty()
}
