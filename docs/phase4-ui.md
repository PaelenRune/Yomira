# Faz 4 — OLED Compose UI

## Mimari

`MainActivity` içindeki Navigation Compose grafiği `home`, `discover`, `library`,
`vault` ve kimliği URI-encoded `details/{id}` rotalarını yönetir.
`:feature:home`, `:feature:vault`, `:feature:details` birbirine bağımlı değildir;
ortak afiş, carousel, ızgara ve navigasyon `:core:ui` içindedir.
Feature ekranları immutable parametreler ve callback'ler alır. Hilt
`YomiraViewModel` Room akışlarını lifecycle-aware Compose aboneliklerine sunar.

## Görsel sistem

- OLED zemin `#06050C`, füme yüzey `#12101F`, mor `#9D4EDD`, fuşya `#FF007F`.
- Puan `#FFB703`, metin `#FFFFFF`, ikincil metin `#8E8D9A`.
- Mevcut `YomiraTypography` korunur; büyük afiş tek baskın görsel öğedir.
- HorizontalPager, afişten üretilen bulanık arka plan, cam puan rozeti ve
  2/3/4 sütunda detay yoğunluğu değişen kartlar kullanılır.
- Android 26–30'da Compose blur desteklenmediği için koyu gradient scrim
  okunabilirliği korur; Android 31+ blur uygular.
- Kapaklar uygulamayla dağıtılmaz. Coil yalnızca katalogdaki uzak URL'leri yükler.

## Veri ve güvenlik

- Ana sayfa, keşfet, kitaplık, arama ve yatay şeritler EXPLICIT kayıtları eler.
- Vault kilitliyken feature'a hiçbir seri verilmez; içeride ikinci kontrol yapılır.
- Vault yalnızca EXPLICIT kayıtları alır. Mature rozetleri normal akışta görünür.
- Sistem ekran kilidi doğrulaması (cihaz PIN/desen/şifre) kullanılır; uygulamada
  varsayılan PIN, parola saklama veya taklit bir doğrulama bulunmaz.
- Kilit açık durumu diske/SavedState'e yazılmaz. Arka plana geçişte kapanır.
- Panik düğmesi kilitler, eşleştirme işini iptal eder ve geçmişi temizleyip
  ana sayfaya döner. Kilitli yetişkin detay rotası görüntülenmez.
- `FLAG_SECURE`, ekran görüntüsünü ve Recents önizlemesini engeller.
- Sütun sayıları ana sayfa/Vault için ayrı; kitaplık ve okundu işaretleri kalıcıdır.
- Son güncellenenler Room'da en son bölüm tarihine göre sıralanır. Trend vitrini
  mevcut modelde popülerlik metriği olmadığından puan sırasıyla kürate edilir.

## Mevcut altyapının sınırları

Depoda katalog dolduran kaynak senkronizasyonu, çalışan Android sandbox host'u,
sayfa okuyucusu veya çevrimdışı indirme motoru yoktur. UI sahte seri, bölüm,
indirme başarısı veya Fansub önerisi üretmez. Mevcut Room kayıtları gösterilir;
boş katalog kullanıcıya açıklanır. Bölüm okuma HTTPS kaynak bağlantısını açar.
İndirme eylemi mevcut sürümün metadata-only sınırını Snackbar ile açıklar;
`ChapterUiState.download` gelecekteki gerçek indirme akışına hazırdır.

Smart Matcher gerçek domain `SmartSourceMatcher.match()` metoduna bağlanır.
Sandbox host hazır olduğunda Hilt'e `SmartSourceMatcher` provider'ı eklenir;
`@BindsOptionalOf` sayesinde mevcut build runtime yokluğunu açık bir
`SmartMatchResult.Unavailable` durumuna çevirir. Öneri varsa kullanıcı onayıyla
HTTPS APK bağlantısı açılır; otomatik veya sessiz kurulum yapılmaz.

## Dosyalar

- `core/ui/src/main/java/com/yomira/core/ui/CatalogComponents.kt`
- `feature/{home,vault,details}/build.gradle.kts`
- `feature/home/src/main/java/com/yomira/feature/home/HomeScreen.kt`
- `feature/vault/src/main/java/com/yomira/feature/vault/VaultScreen.kt`
- `feature/details/src/main/java/com/yomira/feature/details/MangaDetailScreen.kt`
- `app/src/main/java/com/yomira/app/YomiraViewModel.kt`
- `app/src/main/java/com/yomira/app/di/MatcherModule.kt`
- `app/src/main/java/com/yomira/app/MainActivity.kt` (yenilendi)
- `feature/home/src/test/java/com/yomira/feature/home/CatalogIsolationTest.kt`
- `feature/vault/src/test/java/com/yomira/feature/vault/VaultIsolationTest.kt`

Ek güncellemeler: settings, sürüm kataloğu, app/core-ui bağımlılıkları,
`MangaDao.observeRecentlyUpdated()` ve tema puan rengi.

## Doğrulama

JDK 17 ve Android SDK 35 ile:

```powershell
.\gradlew.bat :app:assembleDebug :feature:home:testDebugUnitTest :feature:vault:testDebugUnitTest
```

İzolasyon testleri SAFE/MATURE/EXPLICIT ayrımını, kilitli Vault'u ve tür
filtrelerinin içerik sınırını aşamadığını kontrol eder. Cihaz üstünde sistem
kimlik doğrulaması, ekran okuyucu, büyük yazı ve arka plan kilidi ayrıca test
edilmelidir.
