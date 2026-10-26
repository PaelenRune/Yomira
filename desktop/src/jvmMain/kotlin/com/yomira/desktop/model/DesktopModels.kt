package com.yomira.desktop.model

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.network.MetadataSources
import com.yomira.core.network.RemoteMetadata
import java.text.Normalizer
import java.util.Locale

/**
 * The card the desktop poster grid renders. It is a view model over
 * [RemoteMetadata] / [Manga]; nothing here is persisted and no image bytes are
 * embedded. All URLs remain remote references.
 */
data class CatalogItem(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val score: Double? = null,
    val year: Int? = null,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val author: String? = null,
    val sourceId: String,
    val remoteId: String,
    val sourceUrl: String? = null,
    val contentRating: ContentRating = ContentRating.SAFE,
) {
    val isReadableOnDesktop: Boolean get() = sourceId == MetadataSources.MANGADEX

    /** Stable cache key for artwork: a provider may reuse a URL across cards. */
    val coverKey: String get() = coverUrl ?: id
}

/** One chapter row in the desktop detail drawer. */
data class DesktopChapter(
    val id: String,
    val mangaId: String,
    val number: Double,
    val title: String? = null,
    val group: String? = null,
    val language: String? = null,
    val pageCount: Int = 0,
    /** External chapters are hosted by the scanlator and cannot be read in-app. */
    val externalUrl: String? = null,
) {
    val isReadable: Boolean get() = externalUrl == null

    /** `Bölüm 12.5 · Title`, without inventing a number the source did not publish. */
    val displayLabel: String
        get() = buildString {
            append("Bölüm ").append(number.toDisplayNumber())
            title?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
}

/** One page image resolved from the shared reading source. */
data class DesktopPage(
    val index: Int,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    init {
        require(index >= 0) { "Page index must not be negative" }
        require(url.startsWith("https://")) { "Reader pages must be HTTPS" }
    }
}

/** A chapter that is open in the reader, including its async page state. */
data class ReaderSession(
    val item: CatalogItem,
    val chapter: DesktopChapter,
    val pages: List<DesktopPage> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/** Title identity shared with the waterfall: case-, width- and punctuation-insensitive. */
fun normalizeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

/** Strips provider HTML from synopses before they reach Compose text. */
fun plainText(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    if ('<' !in raw) return raw
    return runCatching { org.jsoup.Jsoup.parse(raw).text().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
}

fun RemoteMetadata.toCatalogItem(): CatalogItem = CatalogItem(
    id = "$sourceId:$remoteId",
    // The waterfall promotes the English display title; the grid matches that rule.
    title = englishTitle?.trim()?.takeIf { it.isNotEmpty() } ?: title,
    coverUrl = coverUrl?.takeIf { it.startsWith("https://") },
    bannerUrl = bannerUrl?.takeIf { it.startsWith("https://") },
    score = score,
    year = year,
    status = status,
    synopsis = plainText(description),
    genres = genres.distinct().take(6),
    author = author,
    sourceId = sourceId,
    remoteId = remoteId,
    sourceUrl = sourceUrl?.takeIf { it.startsWith("https://") },
)

fun Manga.toCatalogItem(): CatalogItem = CatalogItem(
    id = id,
    title = title,
    coverUrl = coverUrl?.takeIf { it.startsWith("https://") },
    bannerUrl = bannerUrl?.takeIf { it.startsWith("https://") },
    score = score,
    status = status,
    synopsis = plainText(synopsis),
    genres = genres.distinct().take(8),
    author = author,
    sourceId = sourceId,
    remoteId = id.substringAfter(':', id),
    contentRating = contentRating,
)

fun Double.toDisplayNumber(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

/** Turkish status label used by cards and the detail header. */
fun MangaStatus.desktopLabel(): String? = when (this) {
    MangaStatus.ONGOING -> "Devam ediyor"
    MangaStatus.COMPLETED -> "Tamamlandı"
    MangaStatus.HIATUS -> "Arada"
    MangaStatus.CANCELLED -> "İptal"
    MangaStatus.UNKNOWN -> null
}

/** Human labels for the desktop shell (single locale, same copy as the Android UI). */
object DesktopCopy {
    const val APP_NAME = "Yomira"
    const val TAGLINE = "Sınırsız katalog. Tek bir OLED tuval."
    const val BROWSE = "Keşfet"
    const val SEARCH = "Ara"
    const val SEARCH_HINT = "Seri, yazar veya tür ara…"
    const val SEARCH_EMPTY = "Aramanla eşleşen seri bulunamadı."
    const val SEARCH_PROMPT = "Ctrl+F ile aramaya başla; şelale altı kaynağı birlikte tarar."
    const val SEARCH_FAILED = "Arama tamamlanamadı. Bağlantını kontrol edip yeniden dene."
    const val FEED_EMPTY = "Katalog şu anda boş yanıt verdi."
    const val FEED_FAILED = "Katalog yüklenemedi. Bağlantını kontrol edip yeniden dene."
    const val RETRY = "Yeniden dene"
    const val PREVIOUS = "Önceki sayfa"
    const val NEXT = "Sonraki sayfa"
    const val FULLSCREEN = "Tam ekran"
    const val EXIT_FULLSCREEN = "Tam ekrandan çık"
    const val DETAILS = "Detaylar"
    const val DETAIL_FAILED = "Seri detayı yüklenemedi; kart bilgileri gösteriliyor."
    const val READ = "Okumaya başla"
    const val CHAPTERS = "Bölümler"
    const val CHAPTERS_LOADING = "Bölümler yükleniyor…"
    const val CHAPTERS_EMPTY = "Bu seri için yayınlanmış bölüm bulunamadı."
    const val CHAPTERS_UNSUPPORTED =
        "Bu kaynak masaüstü okuyucusunda bölüm listesi paylaşmıyor. MangaDex kayıtları doğrudan okunabilir."
    const val CHAPTERS_FAILED = "Bölüm listesi yüklenemedi."
    const val PAGES_LOADING = "Sayfalar hazırlanıyor…"
    const val PAGES_FAILED = "Sayfalar yüklenemedi. Bölümü yeniden açmayı dene."
    const val OPEN_SOURCE = "Kaynak sayfayı aç"
    const val NO_COVER = "Kapak yok"
    const val SCORE = "Puan"
    const val AUTHOR = "Yazar"
    const val SYNOPSIS = "Konu"
    const val CLOSE = "Kapat"
    const val BACK = "Geri"
    const val READER_HINT = "Tekerlek: kaydır · ← →: sayfa · Space: ileri · Esc: geri"
    const val GRID_HINT = "← → sayfa · Space ileri · F11 tam ekran · Ctrl+F ara"
    const val LOADING = "Yükleniyor…"
    const val ADULT_HIDDEN = "Yetişkin içerik masaüstü kataloğunda varsayılan olarak gizlidir."
    const val OFFLINE_READER = "Çevrimdışı okuma bu sürümde yok; sayfalar HTTPS üzerinden akışla yüklenir."
    const val UNSUPPORTED_READER = "Bu kayıt masaüstü okuyucusunda desteklenmiyor."
    const val PAGE = "SAYFA"
    const val RELATED = "İlgili"
    const val CHAPTER_PAGES = "%d sayfa"
    const val EXTERNAL_CHAPTER = "Harici bölüm"
    const val OPEN_EXTERNAL = "Tarayıcıda aç"
    const val JUMP_TOP = "Başa dön"
    const val FILTER_READABLE = "Yalnızca okunabilir"
    const val REFRESH = "Yenile"
    const val POWERED_BY_MANGADEX = "Powered by MangaDex"
    const val MODE_MANGA = "Manga (←)"
    const val MODE_WESTERN = "Batı (→)"
    const val MODE_WEBTOON = "Webtoon"
    const val MODE_SPREAD = "Çift sayfa"
}
