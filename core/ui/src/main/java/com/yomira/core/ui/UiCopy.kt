package com.yomira.core.ui

import androidx.compose.runtime.Immutable

/**
 * All human-readable text rendered by the shared catalog components.
 *
 * The library modules deliberately hold no string resources — the app module owns
 * `values/strings.xml` (English default) and `values-tr/strings.xml` and builds a copy
 * instance per composition, so switching the app locale re-renders every component in
 * the new language. The defaults keep previews and existing instrumentation tests
 * source-compatible.
 */
@Immutable
data class CatalogCopy(
    val readNow: String = "Hemen oku",
    val matureBadgeLong: String = "16+ / Mature",
    val matureBadgeShort: String = "16+",
    val explicitBadgeShort: String = "18+",
    val noCoverArtwork: String = "Kapak görseli yok",
    val scoreDescription: String = "Puan",
    val formatManga: String = "Manga",
    val formatWebtoon: String = "Webtoon",
    val formatManhua: String = "Manhua",
    val formatWestern: String = "Çizgi Roman",
    val columnDescriptions: List<String> = listOf(
        "2 sütun, büyük ve detaylı",
        "3 sütun, standart",
        "4 sütun, kompakt koleksiyon",
    ),
    val navHome: String = "Ana sayfa",
    val navDiscover: String = "Keşfet",
    val navLibrary: String = "Kitaplık",
    val navProfile: String = "Yolculuk",
    val navVault: String = "+18 Vault",
)

/** Texts of the in-app update dialog. */
@Immutable
data class UpdateDialogCopy(
    val title: String = "Yeni Sürüm Mevcut!",
    val changesLabel: String = "Değişiklikler",
    val apkNote: String = "Güncelleme uygulama içinde indirilir, ardından Android kurulum ekranı açılır.",
    val updateNow: String = "Şimdi Güncelle",
    val later: String = "Daha sonra",
    val downloading: String = "İndiriliyor…",
    val install: String = "Kur",
    val failed: String = "Güncelleme tamamlanamadı. Bağlantıyı ve bilinmeyen uygulama yükleme iznini kontrol edip tekrar deneyin.",
)
