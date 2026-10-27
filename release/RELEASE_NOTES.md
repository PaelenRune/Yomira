# Yomira v5.2.0-beta

`v5.2.0-beta` · 27 Ekim 2026 · Android 8.0+ (API 26) · Mobil Odak

[Önceki sürümle karşılaştır](https://github.com/PaelenRune/Yomira/compare/v5.1.0...v5.2.0-beta) · [Sürüm geçmişi](CHANGELOG.md)

---

## 🌟 New & Highlights

- **Saf Android & Mobil Odak:** GitHub ana sayfası, dokümantasyon ve release yapısı masaüstü (Windows/PC) kalıntılarından tamamen arındırıldı. Yomira artık doğrudan saf mobil deneyime odaklıdır.
- **Posterlerin Altında Seri Adı:** Ana sayfa, raflar (Trendler, Popüler, Size Özel vb.) ve katalog ızgarasındaki tüm manga kartlarının altına 2 satırlı, okunaklı seri başlığı eklendi.
- **Kapak Görseli Dil Önceliği:** Uygulamada hangi okuma/arayüz dili seçilirse seçilsin, manga ve webtoon kapaklarında İngilizce kapaklar (`locale == "en"`) birincil öncelikle seçilir. Japonca/orijinal yazılı kapaklar yalnızca İngilizce kapak bulunamadığında fallback olarak devreye girer.
- **İlk Açılış Kapak Hızlandırması (Coil DiskCache):** Coil görüntü yükleyicisine 250 MB özel `DiskCache` ve agresif önbellekleme desteği entegre edildi. İlk açılışta kapaklar hızla yüklenir, sonraki açılışlarda diskten anında (sıfır gecikmeyle) sunulur.
- **İzole & Temiz Sürüm Varlıkları:** Release yayın süreçlerinde geçmiş sürümlerin karışması engellendi; her sürüm yalnızca kendi APK'sını ve bağımsız SHA256 checksum değerini barındırır.

## 🛠️ Fixes & Improvements

- **MangaCard Layout:** Kapak kartı `Box` yapısından `Column` mimarisine dönüştürülerek dokunma alanı genişletildi, başlıklar gölgeli posterin altına taşındı.
- **MangaDex & ArtworkResolver:** Cover art seçiminde `en` filtreleme güçlendirildi; eksik kapak bilgisi durumunda güvenli HTTPS fallback mekanizması korundu.
- **Release Hijyeni:** Staging alanındaki eski paket kalıntıları temizlendi, checksum dosyası `v5.2.0-beta` için tekil olarak üretildi.

## 👥 Contributors

- **[PaelenRune](https://github.com/PaelenRune)** — sürüm yönetimi, mobilizasyon ve UX mimarisi
- Tüm [açık kaynak katkıda bulunanlar](https://github.com/PaelenRune/Yomira/graphs/contributors)

## 📦 Assets

| Asset | Boyut | Hedef |
| :--- | ---: | :--- |
| [`yomira-v5.2.0-beta.apk`](https://github.com/PaelenRune/Yomira/releases/download/v5.2.0-beta/yomira-v5.2.0-beta.apk) | ~24.8 MB | Universal Android (arm64-v8a, armeabi-v7a, x86_64) |
| [`SHA256SUMS.txt`](https://github.com/PaelenRune/Yomira/releases/download/v5.2.0-beta/SHA256SUMS.txt) | — | Bütünlük doğrulaması (Tekil v5.2.0-beta) |

```bash
# Kurulum ve doğrulama
adb install -r yomira-v5.2.0-beta.apk
sha256sum -c SHA256SUMS.txt
```

## 🔗 Bağlantılar

- **Depo:** https://github.com/PaelenRune/Yomira
- **Sürümler:** https://github.com/PaelenRune/Yomira/releases
- **Sorun bildir:** https://github.com/PaelenRune/Yomira/issues
