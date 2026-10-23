# v0.1.0-beta doğrulama raporu

Tarih: 2026-10-23. Platform: Windows, JDK 17, Android SDK 35, Gradle 8.11.1.

## Sonuç

- `:app:assembleDebug`: **başarılı**.
- 18 test sınıfında **96 test**, 0 failure, 0 error, 0 skipped.
- `:app:lintDebug`: **başarılı**, 0 hata / 86 uyarı.
- `git diff --cached --check`: temiz.
- `apksigner verify --verbose --print-certs`: **doğrulandı**, APK Signature Scheme v2.
- `aapt dump badging`: `com.yomira.app`, versionCode 1,
  versionName `0.1.0-beta`, minSdk 26, targetSdk 35.

## APK

- Dosya: `release/yomira-v0.1.0-beta.apk`
- Boyut: **20,247,218 byte**.
- SHA-256: `81a0bb7e0b1ae2757c621f70829c526b760ed6e8f81bd27a8bd5f0335364ffa8`
- İmza: `CN=Android Debug, O=Android, C=US`; üretim anahtarı değildir.
- İmza sertifikası SHA-256:
  `ba89a30920194d7e29f144f80505f3631030cb126f190078c7967b1c37bc5c55`

## Çalıştırılan görevler

```text
gradlew.bat :app:assembleDebug testDebugUnitTest :core:model:test
  :core:network:test :domain:classifier:test :domain:extension:test
  :data:extension:test :data:sync:test :app:lintDebug --console=plain --continue
```

Satırlar yukarıda okunabilirlik için bölünmüştür; tek komut olarak çalıştırılır.
Türkçe çalışma yolunda Gradle/JDK test worker'ı `ClassNotFoundException` verdiği
için aynı kaynaklar ASCII adlı geçici doğrulama dizinine kopyalandı:
`C:\Users\paele\AppData\Local\Temp\opencode\yomira-beta-validation`.
Son görev grubu `BUILD SUCCESSFUL` ile tamamlandı. Üretilen APK hem ana çalışma
dizinindeki `app/build/outputs/apk/debug/app-debug.apk` hem release dizinine kopyalandı.

## Test dağılımı

| Alan | Test |
| --- | ---: |
| Core database | 6 |
| Core model | 6 |
| Network istemcileri/alternatif başlık regresyonu | 5 |
| Eklenti veri katmanı | 17 |
| Waterfall aggregator ve Room cache adaptörü | 8 |
| Sync payload/serialization | 11 |
| İçerik sınıflandırma | 9 |
| Eklenti domain | 12 |
| Sync conflict resolution | 11 |
| Ana sayfa/Vault izolasyonu | 5 |
| Okuyucu ilerlemesi | 6 |
| **Toplam** | **96** |

## Kalan uyarılar ve doğrulanmayanlar

Lint uyarıları: 75 bağımlılık güncellemesi, 6 AGP sürüm önerisi, 2 kullanılmayan
kaynak, 1 KTX önerisi, 1 uygulama simgesi eksikliği, 1 data extraction rules önerisi.
Uyarılar baseline ile gizlenmedi. API 27 gerektiren tema niteliği API 26 uyumluluğu
için kaldırıldı; sistem çubukları mevcut `SystemBarStyle.dark` ile ayarlanır.

SDK platform-tools kurulu/lisansı kabul edilmiş olmadığından cihaz testleri
çalıştırılmadı. Gerçek cihaz kurulumu, UI smoke testi ve Room instrumentation
testleri bu raporun kapsamında **doğrulanmadı**.

OAuth/token UI, güvenilir sandbox host ve metadata içe aktarma UI bağlantıları
henüz yoktur; README ve sürüm notlarında belirtilmiştir. Ağ istemcileri yerel
MockWebServer testleriyle doğrulandı; canlı servis uyumluluğu veya kota garantisi
verilmez. Git uzak deposu yapılandırılmamış; GitHub tag/push/release yayımlanmadı.
