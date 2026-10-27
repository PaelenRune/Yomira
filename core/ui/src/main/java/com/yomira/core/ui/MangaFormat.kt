package com.yomira.core.ui

import androidx.compose.ui.graphics.Color
import com.yomira.core.model.Manga
import com.yomira.core.model.WesternComics
import com.yomira.core.ui.theme.NeonLavender
import com.yomira.core.ui.theme.RatingGold
import java.util.Locale

/**
 * Publication format of a catalog series.
 *
 * Retained for catalog classification and localized metadata. Publication
 * formats are deliberately not displayed as chips on series posters.
 */
enum class MangaFormat {
    MANGA,
    WEBTOON,
    MANHUA,
    WESTERN;

    /** Accent tint available to catalog metadata views. */
    val accent: Color
        get() = when (this) {
            MANGA -> NeonLavender
            WEBTOON -> Color(0xFF34D399)
            MANHUA -> Color(0xFFF472B6)
            WESTERN -> RatingGold
        }

    /** Short localized metadata name. */
    fun label(copy: CatalogCopy): String = when (this) {
        MANGA -> copy.formatManga
        WEBTOON -> copy.formatWebtoon
        MANHUA -> copy.formatManhua
        WESTERN -> copy.formatWestern
    }
}

/**
 * Classifies a series into [MangaFormat] from the genres persisted by every
 * provider. `Manhua` is checked before `Webtoon` because Chinese comics are
 * frequently tagged with both; western comics win over everything else since the
 * bundled catalogue persists an explicit `Western Comic` marker for them.
 */
fun mangaFormatOf(manga: Manga): MangaFormat {
    val genres = manga.genres.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    if (genres.any { it in WesternComics.GENRE_MARKERS }) return MangaFormat.WESTERN
    if (genres.any { it.contains("manhua") || it == "chinese comic" || it == "çin çizgi romanı" }) return MangaFormat.MANHUA
    if (genres.any {
            it.contains("webtoon") || it.contains("manhwa") || it.contains("web comic") ||
                it == "webcomic" || it == "web novel comic"
        }
    ) {
        return MangaFormat.WEBTOON
    }
    return MangaFormat.MANGA
}
