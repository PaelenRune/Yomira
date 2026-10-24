package com.yomira.core.ui

/**
 * All human-readable text rendered by the shared catalog components.
 *
 * The library modules deliberately hold no string resources — the app module owns
 * `values/strings.xml` (English default) and `values-tr/strings.xml` and builds a copy
 * instance per composition, so switching the app locale re-renders every component in
 * the new language. The defaults keep previews and existing instrumentation tests
 * source-compatible.
 */
data class CatalogCopy(
    val readNow: String = "Hemen oku",
    val matureBadgeLong: String = "16+ / Mature",
    val matureBadgeShort: String = "16+",
    val noCoverArtwork: String = "Kapak görseli yok",
    val scoreDescription: String = "Puan",
    val columnDescriptions: List<String> = listOf(
        "2 sütun, büyük ve detaylı",
        "3 sütun, standart",
        "4 sütun, kompakt koleksiyon",
    ),
    val navHome: String = "Ana sayfa",
    val navDiscover: String = "Keşfet",
    val navLibrary: String = "Kitaplık",
    val navVault: String = "+18 Vault",
)

/** Texts of the in-app update dialog. */
data class UpdateDialogCopy(
    val title: String = "Yeni Sürüm Mevcut!",
    val changesLabel: String = "Değişiklikler",
    val apkNote: String = "APK, GitHub Releases sayfası üzerinden tarayıcıda indirilir.",
    val updateNow: String = "Şimdi Güncelle",
    val later: String = "Daha sonra",
)
