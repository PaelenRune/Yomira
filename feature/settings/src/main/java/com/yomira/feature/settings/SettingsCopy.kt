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
    val storageSection: String = "Depolama ve sıfırlama",
    val backfillTitle: String = "Kitaplık adlarını ve kapaklarını yenile",
    val backfillBody: String = "İngilizce → orijinal dil. Yalnız ölçülmeyen ağda, ekran açık ve kilitli değilken çalışır.",
    val backfillProgress: String = "%1\$d / %2\$d · Başarısız: %3\$d",
    val cacheSize: String = "Kullanılan önbellek: %1\$s",
    val loading: String = "Hesaplanıyor…",
    val confirmClearCache: String = "Önbelleği temizlemek istiyor musunuz?",
    val factoryReset: String = "Tüm Verileri Sıfırla (Fabrika Ayarlarına Dön)",
    val factoryResetBody: String = "Kitaplık, okuma geçmişi, eklenti ayarları ve önbellek tamamen silinir. Uygulama ilk kurulum durumuna döner.",
    val factoryResetWarning: String = "Bu işlem geri alınamaz. Uygulama kapanır; yeniden açtığınızda ilk kurulum başlar. Google Drive yedekleri ve dışa aktarılan dosyalar silinmez.",
    val confirmFactoryReset: String = "Tüm uygulama verileri silinsin mi?",
    val reset: String = "Sıfırla",
    val resetting: String = "Tüm veriler sıfırlanıyor…",
    val resetFailed: String = "Sıfırlama tamamlanamadı. Yeniden deneyin veya Android uygulama ayarlarından depolamayı temizleyin.",
    val syncSection: String = "Eşitleme ve yedekleme",
    val connectDrive: String = "Google Hesabı ile Giriş Yap",
    val connectDriveBody: String = "Yedeklerinizi ve okuma geçmişinizi Google Drive’da saklamak için Google hesabınızla giriş yapın.",
    val disconnectDrive: String = "Çıkış Yap / Hesabı Ayır",
    val driveConnected: String = "Google Drive bağlı: %1\$s",
    val driveSyncActive: String = "Otomatik eşitleme etkin",
    val driveSyncManual: String = "Bağlı · Elle eşitleme hazır",
    val authFailed: String = "Google hesabına bağlanılamadı. Lütfen yeniden deneyin.",
    val disconnectFailed: String = "Yerel bağlantı kapatıldı. Google hesap izinlerinizi kontrol edin; erişim iptal edilememiş olabilir.",
    val automaticSync: String = "Cihazlar Arası Otomatik Senkronizasyon",
    val automaticSyncBody: String = "Okuma geçmişiniz, serileriniz ve eklentileriniz Google Drive ile otomatik eşitlenir.",
    val syncSuggestion: String = "Başka cihazda kaldığınız yerden devam etmek için otomatik eşitlemeyi açın.",
    val syncNow: String = "Şimdi Eşitle",
    val syncing: String = "Eşitleniyor…",
    val lastSynced: String = "Son eşitleme: %1\$s",
    val neverSynced: String = "Henüz eşitlenmedi",
    val syncSuccess: String = "Google Drive ile eşitlendi.",
    val signInRequired: String = "Eşitlemek için Google hesabınızla giriş yapın.",
    val syncFailed: String = "Eşitleme tamamlanamadı. Bağlantınızı kontrol edip tekrar deneyin.",
    val cacheSizeFailed: String = "Önbellek boyutu okunamadı. Ayarları yeniden açıp deneyin.",
    val preferenceFailed: String = "Tercih kaydedilemedi. Lütfen yeniden deneyin.",
    val contentLanguage: String = "İçerik dili",
    val languageAll: String = "Tümü",
    val contentLanguageBody: String = "Tümü, Türkçe ve İngilizce kaynakları kapsar.",
    val cacheClearSuccess: String = "Önbellek temizlendi.",

    // Privacy and the +18 Vault.
    val privacySection: String = "Gizlilik ve Kasa",
    val stealthShieldTitle: String = "Gizlilik & Sansür Kalkanı",
    val stealthShieldBody: String = "Harici VPN gerekmeden İSS engellerini ve DNS takibini aşar. Her ağda çalışmayabilir; anonimlik sağlamaz.",
    val vaultTitle: String = "+18 Vault",
    val vaultBody: String = "Kapatınca alt çubuktaki \uD83D\uDD12 +18 Kasa sekmesi tamamen kaybolur; telefonda yetişkin içerik olduğuna dair hiçbir iz kalmaz. Yeniden açmak için cihaz kilidi doğrulanır.",
    val vaultNsfwTitle: String = "Vault’ta Yetişkin İçeriği Göster",
    val vaultNsfwBody: String = "İlk açılıştaki tek seferlik onayın kararı. Kapatınca kasa yine açılır ama raflar boş kalır; içerik gizlenir ve giriş kilitli kalır.",
    val vaultHiddenBadge: String = "GİZLİ",
    val revealDenied: String = "Cihaz kilidi doğrulanamadı; +18 Vault gizli kalmaya devam ediyor.",
    val deviceLockTitle: String = "Cihaz Kilidi ile Koru",
    val deviceLockBody: String = "Vault'a her girişte PIN, desen veya biyometrik doğrulama istenir.",
    val lockBackgroundTitle: String = "Arka Plana Geçince Kilitle",
    val lockBackgroundBody: String = "Uygulama arka plana alındığında Vault anında yeniden kilitlenir.",

    // Extensions and repositories.
    val repositoriesSection: String = "Eklentiler ve depolar",
    val repositoriesSectionBody: String = "Yomira küratörlü depolarla hazır gelir: Keiyoushi evrensel deposu ile Türkçe ve Batı depoları uygulamanın içinde gömülüdür ve ilk açılışta çevrimdışı bile kullanılabilir. İstemeyeceklerini anahtarlarla kapat, özel depo ekle; eklentileri Eklentiler ekranından yükle.",
    val addRepository: String = "Özel Depo Ekle",
    val addRepositoryTitle: String = "Özel Depo Ekle",
    val addRepositoryBody: String = "Depo indeksinin https:// ile başlayan JSON adresini gir.",
    val addRepositoryHint: String = "https://ornek.depo/index.min.json",
    val add: String = "Ekle",
    val cancel: String = "Vazgeç",
    val removeRepository: String = "Depoyu kaldır",
    val invalidRepositoryUrl: String = "Geçersiz adres: depo adresi mutlaka https ile başlayan tam bir JSON indeksi olmalı.",
    val repositoryError: String = "Depo işlemi tamamlanamadı. Bağlantını kontrol edip tekrar dene.",
    val customRepositories: String = "Diğer depoların",

    // Reading and language.
    val readingSection: String = "Dil ve okuma",
    val languageTitle: String = "Arayüz Dili",
    val languageTurkish: String = "Türkçe",
    val languageEnglish: String = "English",
    val readingModeTitle: String = "Varsayılan Okuma Modu",
    val modeWebtoon: String = "Webtoon Dikey",
    val modeManga: String = "Manga RTL",
    val modeWestern: String = "Western LTR",
    val guidedViewTitle: String = "Guided View (Panel Takibi)",
    val guidedViewBody: String = "Sayfalar panel panel, sinematik yakınlaşmayla okunur; tek dokunuşla açılıp kapatılır.",

    // Application and cache.
    val appSection: String = "Uygulama Bilgisi",
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

/** Settings-owned copy for controls previously hardcoded in the screen. */
internal data class SettingsContentCopy(
    val filtersSection: String = "İçerik filtreleri",
    val contentLevel: String = "Yaş ve içerik düzeyi",
    val safeMode: String = "Güvenli mod",
    val adultNormal: String = "18+ · Normal",
    val adultVault: String = "18+ · Kasa",
    val excludedTagsBody: String = "Seçili etiketlerdeki seriler her yerde gizlenir.",
    val rerunSurvey: String = "Okuma anketini yeniden yap",
    val gore: String = "Gore / Aşırı şiddet",
    val universalTitle: String = "Keiyoushi evrensel deposu",
    val universalBody: String = "Uygulamada gömülü · Farklı dillerde topluluk kaynakları.",
    val turkishTitle: String = "Türkçe çizgi roman ve manga deposu",
    val turkishBody: String = "Uygulamada gömülü · Türkçe okuma kaynakları.",
    val westernTitle: String = "Batı çizgi romanları deposu",
    val westernBody: String = "Uygulamada gömülü · Batı çizgi romanı ve webcomic kaynakları.",
) {
    fun repositoryTitle(repository: CuratedRepository): String = when (repository.id) {
        CuratedRepositories.UNIVERSAL.id -> universalTitle
        CuratedRepositories.TURKISH_COMICS.id -> turkishTitle
        CuratedRepositories.WESTERN_COMICS.id -> westernTitle
        else -> repository.title
    }

    fun repositoryBody(repository: CuratedRepository): String = when (repository.id) {
        CuratedRepositories.UNIVERSAL.id -> universalBody
        CuratedRepositories.TURKISH_COMICS.id -> turkishBody
        CuratedRepositories.WESTERN_COMICS.id -> westernBody
        else -> repository.subtitle
    }

    companion object {
        fun forLanguage(language: AppLanguage): SettingsContentCopy = when (language) {
            AppLanguage.TURKISH -> SettingsContentCopy()
            AppLanguage.ENGLISH -> SettingsContentCopy(
                filtersSection = "Content filters",
                contentLevel = "Age and content level",
                safeMode = "Safe mode",
                adultNormal = "18+ · Normal",
                adultVault = "18+ · Vault",
                excludedTagsBody = "Series with selected tags are hidden everywhere.",
                rerunSurvey = "Retake reading survey",
                gore = "Gore / Extreme violence",
                universalTitle = "Keiyoushi universal repository",
                universalBody = "Bundled in-app · Community sources in multiple languages.",
                turkishTitle = "Turkish comics and manga repository",
                turkishBody = "Bundled in-app · Turkish reading sources.",
                westernTitle = "Western comics repository",
                westernBody = "Bundled in-app · Western comics and webcomic sources.",
            )
        }
    }
}
