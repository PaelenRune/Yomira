package com.yomira.core.database.converter

import androidx.room.TypeConverter
import com.yomira.core.model.ContentRating
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import java.util.Date
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stable enum values and JSON keep persisted data independent of ordinal order and delimiters. */
class DatabaseConverters {
    @TypeConverter
    fun stringListToJson(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun dateToEpochMillis(value: Date?): Long? = value?.time

    @TypeConverter
    fun epochMillisToDate(value: Long?): Date? = value?.let(::Date)

    @TypeConverter
    fun contentRatingToLevel(value: ContentRating): Int = value.level

    @TypeConverter
    fun levelToContentRating(value: Int): ContentRating = ContentRating.fromLevel(value)

    @TypeConverter
    fun readingModeToString(value: ReadingMode): String = value.name

    @TypeConverter
    fun stringToReadingMode(value: String): ReadingMode = ReadingMode.valueOf(value)

    @TypeConverter
    fun mangaStatusToString(value: MangaStatus): String = value.name

    @TypeConverter
    fun stringToMangaStatus(value: String): MangaStatus = MangaStatus.valueOf(value)
}
