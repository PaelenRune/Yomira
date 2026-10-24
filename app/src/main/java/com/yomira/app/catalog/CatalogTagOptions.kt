package com.yomira.app.catalog

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yomira.app.R
import com.yomira.core.ui.CatalogTags
import com.yomira.core.ui.TagChip

/**
 * Maps every [CatalogTags] id to its localized label resource. English comes from
 * `values/strings.xml`, Turkish from `values-tr`; a missing mapping degrades to the
 * raw id instead of hiding the chip, so a new tag can never silently vanish.
 */
@StringRes
internal fun tagLabelRes(tagId: String): Int = when (tagId) {
    "action" -> R.string.tag_action
    "adventure" -> R.string.tag_adventure
    "comedy" -> R.string.tag_comedy
    "drama" -> R.string.tag_drama
    "ecchi" -> R.string.tag_ecchi
    "fantasy" -> R.string.tag_fantasy
    "horror" -> R.string.tag_horror
    "isekai" -> R.string.tag_isekai
    "mystery" -> R.string.tag_mystery
    "romance" -> R.string.tag_romance
    "sci-fi" -> R.string.tag_sci_fi
    "slice-of-life" -> R.string.tag_slice_of_life
    "sports" -> R.string.tag_sports
    "supernatural" -> R.string.tag_supernatural
    "thriller" -> R.string.tag_thriller
    "psychological" -> R.string.tag_psychological
    "harem" -> R.string.tag_harem
    "mature" -> R.string.tag_mature
    "nudity" -> R.string.tag_nudity
    "adult-cast" -> R.string.tag_adult_cast
    "gore" -> R.string.tag_gore
    "seinen" -> R.string.tag_seinen
    "shounen" -> R.string.tag_shounen
    "shoujo" -> R.string.tag_shoujo
    "josei" -> R.string.tag_josei
    "kids" -> R.string.tag_kids
    "school" -> R.string.tag_school
    "military" -> R.string.tag_military
    "mecha" -> R.string.tag_mecha
    "music" -> R.string.tag_music
    "cooking" -> R.string.tag_cooking
    "historical" -> R.string.tag_historical
    "vampire" -> R.string.tag_vampire
    "demons" -> R.string.tag_demons
    "magic" -> R.string.tag_magic
    "martial-arts" -> R.string.tag_martial_arts
    "super-power" -> R.string.tag_super_power
    "video-game" -> R.string.tag_video_game
    "webtoon" -> R.string.tag_webtoon
    "western" -> R.string.tag_western
    "gender-bender" -> R.string.tag_gender_bender
    "award-winning" -> R.string.tag_award_winning
    else -> 0
}

/** Localized chips for the multi-select tag bar, in shelf order. */
@Composable
fun catalogTagChips(): List<TagChip> = CatalogTags.all.map { tag ->
    val labelRes = tagLabelRes(tag.id)
    TagChip(id = tag.id, label = if (labelRes == 0) tag.id else stringResource(labelRes))
}
