# Yomira v5.1.0

`v5.1.0` · 27 Ekim 2026 · Android 8.0+ (API 26) · Windows 10/11

[Önceki sürümle karşılaştır](https://github.com/PaelenRune/Yomira/compare/v5.0.0...v5.1.0) · [Sürüm geçmişi](CHANGELOG.md)

---

## 🌟 New & Highlights

- **Dile duyarlı kaynak eşleştirme:** Onboarding veya ayarlarda seçilen dil (TR/EN) kaynak
  eşleştirme motoruna dinamik olarak bağlandı; doğru dildeki kaynaklar en yüksek öncelikle
  sıralanıyor.
- **Xiaomi MIUI ve Android 12 uyumluluk optimizasyonları.** MIUI'nin agresif süreç yönetimi ve
  API 31 kısıtları altında soğuk açılış, kurulum ve arayüz yaşam döngüsü stabil.
- **Windows eş sürüm.** `Yomira-5.1.0.exe` ve `.msi`, Android ile aynı kaynak ağacından üretildi.

## 🛠️ Fixes

- **Android 12 & MIUI başlangıç çökmesi giderildi.** Room Database, Hilt Dependency Injection,
  kotlinx.serialization ve WorkManager sınıfları için R8 ProGuard keep kuralları eklendi; release
  derlemesi ilk açılışta yansımayla yüklenen sınıfları artıkelmiyor.
- **APK derleme boyutları optimize edildi.** Universal paket ~24.8 MB'a indi; indirme boyutu ve
  kurulum süresi düştü.
- **Kararlılık.** Arka planda güvenli arayüz geçişleri sağlandı, kaynak eşleştirme kararlılığı
  artırıldı.

## 👥 Contributors

- **[PaelenRune](https://github.com/PaelenRune)** — sürüm hazırlığı, R8 keep kuralları,
  MIUI / Android 12 doğrulaması
- Tüm [açık kaynak katkıda bulunanlar](https://github.com/PaelenRune/Yomira/graphs/contributors)

## 📦 Assets

| Asset | Boyut | Hedef |
| :--- | ---: | :--- |
| [`yomira-arm64-v8a-v5.1.0.apk`](https://github.com/PaelenRune/Yomira/releases/download/v5.1.0/yomira-arm64-v8a-v5.1.0.apk) | ~24.5 MB | Modern 64-bit telefon ve tabletler |
| [`yomira-armeabi-v7a-v5.1.0.apk`](https://github.com/PaelenRune/Yomira/releases/download/v5.1.0/yomira-armeabi-v7a-v5.1.0.apk) | ~22 MB | 32-bit Android cihazlar |
| [`yomira-x86_64-v5.1.0.apk`](https://github.com/PaelenRune/Yomira/releases/download/v5.1.0/yomira-x86_64-v5.1.0.apk) | ~26 MB | Emülatör ve x86 cihazlar |
| [`yomira-v5.1.0.apk`](https://github.com/PaelenRune/Yomira/releases/download/v5.1.0/yomira-v5.1.0.apk) (Universal) | ~24.8 MB | Tüm ABI'lar tek pakette |
| [`Yomira-5.1.0.exe`](https://github.com/PaelenRune/Yomira/releases/download/v5.1.0/Yomira-5.1.0.exe) | — | Windows masaüstü, taşınabilir |
| [`Yomira-5.1.0.msi`](https://github.com/PaelenRune/Yomira/releases/download/v5.1.0/Yomira-5.1.0.msi) | — | Windows masaüstü, kurulum paketi |
| [`SHA256SUMS.txt`](https://github.com/PaelenRune/Yomira/releases/download/v5.1.0/SHA256SUMS.txt) | — | Bütünlük doğrulaması |

> Emin değilsen **Universal** APK'yı indir. Bölünmüş paketler aynı imzayı taşır; v5.0.0 ve
> öncesi kurulumların üzerine doğrudan kurulur, kütüphane ve ilerleme korunur.

```bash
# Android — kurulum ve doğrulama
adb install -r yomira-arm64-v8a-v5.1.0.apk
sha256sum -c SHA256SUMS.txt
```

## 🔗 Bağlantılar

- **Depo:** https://github.com/PaelenRune/Yomira
- **Sürümler:** https://github.com/PaelenRune/Yomira/releases
- **Sorun bildir:** https://github.com/PaelenRune/Yomira/issues
