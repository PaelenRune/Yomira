package com.yomira.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yomira.core.model.Manga
import java.text.Normalizer
import java.util.Locale

/**
 * One entry of the catalog tag matrix.
 *
 * [id] is locale-independent and persisted in UI state; [aliases] are the spellings that
 * actually appear in persisted provider genres — AniList/MangaDex/MangaUpdates deliver
 * English labels ("Action", "Sci-Fi"), the bundled seed adds variants ("Western Comic",
 * "Adult Webtoons"), and Turkish sources may already carry translated names ("Aksiyon").
 * A tag matches when the series carries **any** of its aliases.
 */
data class CatalogTag(val id: String, val aliases: Set<String>) {
    /** True when [genre] (a persisted provider genre) represents this tag. */
    fun matchesGenre(genre: String): Boolean {
        val value = normalizeGenre(genre)
        if (value.isEmpty()) return false
        return aliases.any { alias ->
            val needle = normalizeGenre(alias)
            needle.isNotEmpty() && (value == needle || " $value ".contains(" $needle "))
        }
    }

    companion object {
        /** Unicode-folded, punctuation-stripped comparison form shared by tag matching and tests. */
        fun normalizeGenre(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }
}

/**
 * The 42-tag filtering matrix (spec: 30+). Order is the shelf order: the combinations
 * users actually tap first (Ecchi + Comedy, Action + Isekai …) sit at the front.
 */
object CatalogTags {
    val all: List<CatalogTag> = listOf(
        CatalogTag("action", setOf("Action", "Aksiyon", "Action (seinen)", "Action (shounen)")),
        CatalogTag("adventure", setOf("Adventure", "Macera")),
        CatalogTag("comedy", setOf("Comedy", "Komedi", "Humor", "Slapstick", "Parody")),
        CatalogTag("drama", setOf("Drama", "Dram", "Melodrama")),
        CatalogTag("ecchi", setOf("Ecchi", "Fanservice")),
        CatalogTag("fantasy", setOf("Fantasy", "Fantastik", "High Fantasy", "Dark Fantasy")),
        CatalogTag("horror", setOf("Horror", "Korku", "Psychological Horror")),
        CatalogTag("isekai", setOf("Isekai", "İsekai", "Reincarnation", "Transported to Another World", "Parallel World", "Another World", "Game World", "Time Travel")),
        CatalogTag("mystery", setOf("Mystery", "Gizem", "Detective", "Noir")),
        CatalogTag("romance", setOf("Romance", "Romantizm", "Romantic", "Love Story", "Shojo Ai", "Josei Ai")),
        CatalogTag("sci-fi", setOf("Sci-Fi", "Science Fiction", "Bilim Kurgu", "Cyberpunk", "Space", "Mecha Sci-Fi")),
        CatalogTag("slice-of-life", setOf("Slice of Life", "Dilim Hayat", "Günlük Yaşam", "Daily Life", "Iyashikei", "Cotidiano")),
        CatalogTag("sports", setOf("Sports", "Sport", "Spor", "Soccer", "Baseball", "Basketball", "Martial Arts Sport")),
        CatalogTag("supernatural", setOf("Supernatural", "Doğaüstü", "Occult", "Spiritual Power")),
        CatalogTag("thriller", setOf("Thriller", "Gerilim", "Suspense", "Psychological Thriller")),
        CatalogTag("psychological", setOf("Psychological", "Psikolojik", "Mind Games", "Psychological Drama")),
        CatalogTag("harem", setOf("Harem", "Reverse Harem", "Harems", "Polygamy")),
        CatalogTag("mature", setOf("Mature", "Olgun", "Mature Themes", "Adult", "16+")),
        CatalogTag("nudity", setOf("Nudity", "Çıplaklık", "Nudity (non-sexual)", "Partial Nudity", "Censorship-Free Nudity")),
        CatalogTag("adult-cast", setOf("Adult Cast", "Yetişkin Kadro", "Office Workers", "Working Adults")),
        CatalogTag("gore", setOf("Gore", "Violent", "Violence", "Şiddet", "Guro")),
        CatalogTag("seinen", setOf("Seinen", "Young adult (seinen)")),
        CatalogTag("shounen", setOf("Shounen", "Shonen", "Şounen", "Youth (shounen)")),
        CatalogTag("shoujo", setOf("Shoujo", "Shojo", "Şoujo", "Youth (shoujo)")),
        CatalogTag("josei", setOf("Josei", "Adult (josei)")),
        CatalogTag("kids", setOf("Kids", "Children", "Çocuk", "Kodomomuke")),
        CatalogTag("school", setOf("School", "School Life", "Okul", "Okul Hayatı", "Middle School", "High School", "Kindergarten")),
        CatalogTag("military", setOf("Military", "Askeri", "War", "Savaş", "Strategy Self-Defense")),
        CatalogTag("mecha", setOf("Mecha", "Mech", "Robot", "Mecha (themed)")),
        CatalogTag("music", setOf("Music", "Müzik", "Idols (male)", "Idols (female)", "Band")),
        CatalogTag("cooking", setOf("Cooking", "Food", "Yemek", "Gourmet", "Restaurant")),
        CatalogTag("historical", setOf("Historical", "History", "Tarih", "Tarihi", "Samurai", "Edo Period", "Ancient China")),
        CatalogTag("vampire", setOf("Vampire", "Vampir", "Vampires", "Female Vampire")),
        CatalogTag("demons", setOf("Demons", "Demon", "Şeytan", "Devil", "Fiend")),
        CatalogTag("magic", setOf("Magic", "Büyü", "Magical", "Spell", "Witch", "Witches", "Mage")),
        CatalogTag("martial-arts", setOf("Martial Arts", "Dövüş Sanatı", "Fighting", "Fight", "Combat", "Kung Fu")),
        CatalogTag("super-power", setOf("Super Power", "Superpower", "Super Power (themed)", "Süper Güç", "Superhero", "Süper Kahraman", "Shapeshifting", "Immortality")),
        CatalogTag("video-game", setOf("Video Game", "Game", "Oyun", "Virtual Reality", "MMORPG")),
        CatalogTag("webtoon", setOf("Webtoon", "Webtoons", "Manhwa", "Manhua", "Adult Webtoons", "Web Comic", "Web Comic (themed)")),
        CatalogTag("western", setOf("Western", "Western Comic", "Comic", "American Comic", "Graphic Novel", "Cowboy")),
        CatalogTag("gender-bender", setOf("Gender Bender", "Cinsiyet Değişimi", "Crossdressing", "Body Swap")),
        CatalogTag("award-winning", setOf("Award Winning", "Awards", "Ödüllü", "Manga Taisho Prize", "Kodansha Manga Award")),
    )

    val byId: Map<String, CatalogTag> = all.associateBy { it.id }

    /** Resolves persisted genre strings into the tag ids the series actually carries. */
    fun tagIdsFor(manga: Manga): Set<String> =
        all.filter { tag -> manga.genres.any(tag::matchesGenre) }.mapTo(mutableSetOf()) { it.id }
}

/**
 * Applies the multi-tag matrix to a catalog page.
 *
 * [matchAll] = true is the AND combination the task calls for — selecting `[Ecchi] +
 * [Komedi]` narrows the shelf to ecchi comedies. With [matchAll] = false the same
 * selection behaves as a union. Empty selection returns the input untouched.
 */
fun filterByTags(manga: List<Manga>, selectedTagIds: List<String>, matchAll: Boolean = true): List<Manga> {
    if (selectedTagIds.isEmpty()) return manga
    val tags = selectedTagIds.mapNotNull(CatalogTags.byId::get)
    if (tags.isEmpty()) return manga
    return manga.filter { item ->
        val carried = CatalogTags.tagIdsFor(item)
        if (matchAll) tags.all { it.id in carried } else tags.any { it.id in carried }
    }
}

/** One selectable chip in the tag bar: stable [id] plus the localized [label]. */
data class TagChip(val id: String, val label: String)

/**
 * Horizontally scrollable multi-select tag bar. Chips toggle independently so the user
 * can stack filters (`Ecchi` + `Comedy`); the trailing clear chip resets the selection.
 */
@Composable
fun MultiTagFilterBar(
    tags: List<TagChip>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    clearLabel: String = "Filtreleri temizle",
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tags, key = { it.id }) { chip ->
            val isSelected = chip.id in selected
            // FilterChip already publishes the selected state through its switch
            // semantics; the localized label is the accessible name.
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(chip.id) },
                label = { Text(chip.label) },
            )
        }
        if (selected.isNotEmpty()) {
            item(key = "clear-tags") {
                FilterChip(
                    selected = true,
                    onClick = onClear,
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(clearLabel, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}
