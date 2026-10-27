# Yomira v5.4.0-beta

27 Ekim 2026 · Android 8.0+ (API 26) · versionCode `9`

## Yenilikler ve iyileştirmeler

- Uygulama içi APK indirme, ilerleme gösterimi ve Android paket yükleyicisine geçiş akışı.
- Kaynak eşleştirme, bölüm adresi çözümleme ve eşleşmiş kaynaklardan okuma entegrasyonu.
- Gömülü depo yükleri, depo senkronizasyonu ve katalog tekilleştirme iyileştirmeleri.
- Ana sayfa, seri detayı, kapaklar, Kasa arayüzü ve içerik sınıflandırma güncellemeleri.

## APK

- Dosya: [`yomira-v5.4.0-beta.apk`](yomira-v5.4.0-beta.apk)
- Boyut: **10.978.918 bayt**.
- Paket: `com.yomira.app`; sürüm: `5.4.0-beta` / `9`.
- İmza: `CN=Yomira Release`, APK Signature Scheme v2; mevcut kalıcı anahtar korunur.
- SHA-256: `c86a7b9959126d24b62e6e7245c8577a9601f225eb15e0352b42444d1ed6b38a`

```sh
adb install -r yomira-v5.4.0-beta.apk
sha256sum -c SHA256SUMS.txt
```

## Doğrulama

Release derlemesi ve APK imzası doğrulandı. Xiaomi M2007J3SY / Android 12 (API 31) üzerinde mevcut kurulumun üzerine yükleme ve soğuk açılış başarılı; açılış sonrası süreç canlı ve ön planda, sürece ait crash kaydı yok.

Bu kontrol tam işlevsel kabul testi değildir; tüm kaynakların canlı ağ üzerinden okunması ve güncelleme akışının uçtan uca kurulumu bu smoke testinde doğrulanmadı. Ayrıntılar: [VALIDATION_5.4.0-beta.md](VALIDATION_5.4.0-beta.md).

[Sürüm geçmişi](CHANGELOG.md) · [GitHub](https://github.com/PaelenRune/Yomira)
