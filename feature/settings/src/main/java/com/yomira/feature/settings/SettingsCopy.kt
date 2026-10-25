package com.yomira.feature.settings

/**
 * Localized texts of the settings screen. Turkish is the default for previews;
 * the app fills every field from `values/strings.xml` / `values-tr/strings.xml`
 * so the whole surface follows the active per-app locale.
 */
data class SettingsCopy(
    val title: String = "Ayarlar",
    val subtitle: String = "Gizlilik, depolar ve okuma deneyimi",
    val back: String = "Geri",

    // Section 1 — privacy and the +18 Vault.
    val privacySection: String = "Gizlilik ve +18 Vault",
    val vaultTitle: String = "+18 Vault",
    val vaultBody: String = "Kapatınca alt çubuktaki \uD83D\uDD12 +18 Kasa sekmesi tamamen kaybolur; telefonda yetişkin içerik olduğuna dair hiçbir iz kalmaz. Yeniden açmak için cihaz kilidi doğrulanır.",
    val vaultHiddenBadge: String = "GİZLİ",
    val revealDenied: String = "Cihaz kilidi doğrulanamadı; +18 Vault gizli kalmaya devam ediyor.",
    val deviceLockTitle: String = "Cihaz Kilidi ile Koru",
    val deviceLockBody: String = "Vault'a her girişte PIN, desen veya biyometrik doğrulama istenir.",
    val lockBackgroundTitle: String = "Arka Plana Geçince Kilitle",
    val lockBackgroundBody: String = "Uygulama arka plana alındığında Vault anında yeniden kilitlenir.",

    // Section 2 — extension and repository manager.
    val repositoriesSection: String = "Eklenti ve Depo Yöneticisi",
    val repositoriesSectionBody: String = "Kaynak depolarını tek dokunuşla bağla veya kaldır; seçimler cihazda kalıcı saklanır.",
    val addRepository: String = "Özel Depo Ekle",
    val addRepositoryTitle: String = "Özel Depo Ekle",
    val addRepositoryBody: String = "Depo indeksinin https:// ile başlayan JSON adresini gir.",
    val addRepositoryHint: String = "https://ornek.depo/index.min.json",
    val add: String = "Ekle",
    val cancel: String = "Vazgeç",
    val removeRepository: String = "Depoyu kaldır",
    val invalidRepositoryUrl: String = "Geçersiz adres: depo adresi mutlaka https ile başlayan tam bir JSON indeksi olmalı.",
    val repositoryError: String = "Depo işlemi tamamlanamadı. Bağlantını kontrol edip tekrar dene.",
    val customRepositories: String = "Özel depoların",

    // Section 3 — reading and language.
    val readingSection: String = "Okuma ve Dil",
    val languageTitle: String = "Arayüz Dili",
    val languageTurkish: String = "Türkçe",
    val languageEnglish: String = "English",
    val readingModeTitle: String = "Varsayılan Okuma Modu",
    val modeWebtoon: String = "Webtoon Dikey",
    val modeManga: String = "Manga RTL",
    val modeWestern: String = "Western LTR",
    val guidedViewTitle: String = "Guided View (Panel Takibi)",
    val guidedViewBody: String = "Sayfalar panel panel, sinematik yakınlaşmayla okunur; tek dokunuşla açılıp kapatılır.",

    // Section 4 — application and cache.
    val appSection: String = "Uygulama ve Önbellek",
    val clearCache: String = "Önbelleği Temizle",
    val clearCacheBody: String = "Kapak görselleri ve geçici dosyalar silinir; kütüphanen ve ilerlemen etkilenmez.",
    val cacheCleared: String = "%1\$s önbellek boşaltıldı.",
    val cacheFailed: String = "Önbellek temizlenemedi.",
    val checkUpdates: String = "Güncellemeleri Denetle",
    val updateChecking: String = "Denetleniyor…",
    val updateUpToDate: String = "Zaten güncelsin (v%1\$s).",
    val updateAvailable: String = "Yeni sürüm hazır: v%1\$s",
    val updateDownload: String = "Güncellemeyi indir",
    val versionTitle: String = "Sürüm",
    val githubTitle: String = "GitHub'da Yomira",
    val githubBody: String = "Kaynak kod, sürüm notları ve destek.",
)
