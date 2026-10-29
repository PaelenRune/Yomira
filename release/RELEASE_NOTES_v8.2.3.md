# 🌟 Yomira v8.2.3 — Sürüm Notları

Bu sürüm eklenti ekranına kimlik kazandırır: **eklenti deposu ve kurulu eklenti
listelerindeki her satır artık kaynak sitesinin logosunu gösterir.** Logoyu bulamayan
satır sitenin favicon'unu dener, o da yoksa adın baş harfleri koyu temaya dost bir
plakada belirir.

> **TR:** v8.2.3 eklenti deposu ve kurulu eklenti listelerine kaynak-site logolarını
> getirir; ikon yoksa sitenin favicon'una, o da yoksa isim baş harflerine düşer ve logo
> plakası koyu/OLED temada da düzgün görünür.
>
> **EN:** v8.2.3 shows source-site logos in the extension repository and installed
> extension lists; when the icon is missing it falls back to the site favicon, then to
> the name's initial letters, on a plate that stays legible in dark/OLED themes.

---

### 🖼️ Kaynak-Site Logoları ve Akıllı Fallback

- **Depo ve kurulu eklenti satırları:** Eklenti ekranında hem depo listesindeki hem de
  kurulu/güncellenebilir eklenti listesindeki satırlar yuvarlak bir logo plakası
  kazanır. Kurulu eklentiler depo dizininde bildirilen ikonu kullanır; depo satırları
  ve ikonu olmayan eklentiler sitenin favicon'una düşer.
- **Baş harf fallback'i:** Ne ikon ne favicon yüklenemezse plakada adın ilk iki
  kelimesinin baş harfleri görünür; ad boşsa "?" gösterilir. Kötü bir artwork hiçbir
  zaman eklentinin kullanılabilirliğini bozmaz.
- **Koyu tema dostu plaka:** Logo, koyu bir zemin plakasında `Fit` ölçeğiyle oturur;
  şeffaf favicon'lar ve açık renkli logolar OLED/koyu temada da düzgün görünür.
- **Güvenli ve tutumlu:** Yalnızca HTTPS ikon adresleri kabul edilir; logolar URL
  anahtarlı disk önbelleğinde tutulur, böylece aynı depodaki tüm eklentiler tek favicon
  indirimiyle yetinir. `.ico` dosyaları (PNG kareli veya DIB kareli) uygulama içinde
  çözümlenir.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.2.3` (versionCode `27`) |
| Testler | Kabul kapısı `:app:testReleaseUnitTest` **186/186** ve `assembleRelease` başarılı (sürüm kaynağında yeşil); geliştirme worktree'lerinde modül kapıları — domain/extension **40**, data/extension **103**, app extensions **14** test — ve merge-sonrası ana ağaç doğrulaması (186 test 0/0/0 + `assembleRelease` 4m 50s) tamamen yeşil. Bu sürümde **17 yeni birim testi** |
| Ekli APK | `yomira-v8.2.3.apk` — boyutu ve SHA-256'sı için bu sürüme iliştirilen `SHA256SUMS.txt` (CI, yayın anında eklediği APK için üretir) |
| Doğrulama raporu | `release/VALIDATION_v8.2.3.md` — ham sayılar, commit envanteri ve cihaz kontrol listesi |

APK, sürüm commit'i üzerinden repodaki imza anahtarıyla derlenmiştir; imza sertifikası
önceki sürümlerle aynı olduğundan mevcut kurulumların üzerine sorunsuz güncellenir.
Logoların cihaz üzeri görsel doğrulaması henüz yapılmadı; madde madde durum
[VALIDATION_v8.2.3.md](VALIDATION_v8.2.3.md) içindeki telefon kontrol listesinde
"bekliyor" olarak işaretlidir.
