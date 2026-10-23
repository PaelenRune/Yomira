# Yomira v0.1.0-beta

İlk Android geliştirici betası — Universal Comic & Manga Ecosystem.

## Bu sürüm

- Kotlin/Compose tabanlı OLED vitrin, ayrı 2/3/4 sütun tercihleri.
- Level 0 SAFE / Level 1 MATURE / Level 2 EXPLICIT sınıflandırması ve Vault izolasyonu.
- BakaUpdates → AniList → MangaDex → Kitsu → ComicVine metadata waterfall'ı, Room cache.
- Kitsu kategori/yaş derecelendirmesi ve isteğe bağlı anahtarlı ComicVine istemcisi.
- Keiyoushi depo/eklenti yönetimi, kaynak eşleştirme ve sandbox bridge altyapısı.
- Google Drive AppData sync taşıması, WorkManager ve çatışma çözümü altyapısı.
- Okuyucu ekranları, yakınlaştırma ve yerel ilerleme takibi.

## Bilinen sınırlar

- Geliştirici betasıdır; debug anahtarıyla imzalıdır, üretim sürümü değildir.
- İçerik ve kaynaklar uygulamayla dağıtılmaz. Temiz kurulumda katalog boş olabilir.
- Metadata içe aktarma UI'si, güvenilir eklenti host'u ve sayfa sağlayıcısı bağlı değildir.
- Google OAuth ekranı/token sağlayıcısı bağlanmadığından Drive sync pasiftir.
- ComicVine API anahtarı olmadan ComicVine adımı atlanır.
- Cihaz/emülatör doğrulaması yapılmadan sürümün gerçek cihaz davranışı garanti edilmez.

## Kurulum

Android 8.0+ (API 26+). `yomira-v0.1.0-beta.apk` dosyasını yükleyin.
`SHA256SUMS.txt` ile dosya bütünlüğünü doğrulayın.

## GitHub yayınlama kontrol listesi

- [ ] Fiziksel cihaz/emülatörde kurulum, gezinme ve Vault kilit smoke testi.
- [ ] Debug imzası ve beta sınırları kullanıcıya açıkça bildirildi.
- [ ] Commit'e `v0.1.0-beta` etiketi eklendi ve uzak depoya gönderildi.
- [ ] GitHub'da **Pre-release** açıldı; APK ve SHA256SUMS.txt eklendi.

Örnek (yayıncı tarafından ayrıca çalıştırılır):

```powershell
git tag -a v0.1.0-beta -m "Yomira v0.1.0-beta"
git push origin HEAD
git push origin v0.1.0-beta
gh release create v0.1.0-beta release/yomira-v0.1.0-beta.apk release/SHA256SUMS.txt --prerelease --title "Yomira v0.1.0-beta" --notes-file release/RELEASE_NOTES.md
```
