# 🌟 Yomira v8.2.0 — Sürüm Notları

Bu sürüm içerik derecelendirmesini üç net kademeyle kurar: **SAFE (herkese açık), MATURE
(16+) ve EXPLICIT (18+)**. Artık 18+ tercih eden kullanıcılar ana sayfa, keşif ve aramada
yetişkin temalı serileri de görüyor; açık içerik ise her zamanki gibi yalnızca kilitli
Vault'ta. Vault ilk açılışında tek seferlik bir yetişkin içerik onayı soruyor ve kurulum
adımları, her sorunun neden sorulduğunu açıklayan metinlerle yenilendi.

> **TR:** v8.2.0 içerik kademelerini (SAFE/MATURE/EXPLICIT) tek bir eşleme katmanıyla
> netleştirir; seri detayına 16+/18+ rozetleri ekler, Vault'a ilk-kullanım NSFW onayı
> getirir ve kurulum adımlarını açıklamalı TR/EN metinlerle yeniler.
>
> **EN:** v8.2.0 settles content rating into three tiers (SAFE/MATURE/EXPLICIT) behind a
> single mapping layer, adds 16+/18+ badges on series detail, brings a first-use NSFW
> consent to the Vault, and refreshes onboarding with annotated TR/EN copy.

---

### 🎬 Üç Kademe: SAFE / MATURE / EXPLICIT

- **Kademeler netleşti:** İçerik artık üç kademede değerlendirilir — herkese açık (SAFE),
  yetişkin temalı (MATURE, 16+) ve açık (EXPLICIT, 18+). Seri detayında kademe, mevcut
  rozet düzeninin içinde 16+ / 18+ etiketiyle görünür.
- **Yerleşim matrisi:** Güvenli mod yalnızca SAFE serilerini gösterir. 18+ tercih ettiysen
  ana sayfa, keşif, arama ve raflarda MATURE seriler de listelenir. EXPLICIT içerik ise
  yalnızca Vault kilidinin arkasındadır — v8.1.4'ün herkese açık yüz garantisi aynen korunur.
- **Tüm kaynaklar tek eşleme katmanında:** MangaDex `contentRating` değeri, AniList
  `isAdult` bayrağı ve ecchi/nudity/sexual-theme etiketleri, Kitsu `ageRating` + `nsfw`
  bayrağı ile Baka'nın Mature tür etiketleri tek bir katmanda kademeye çevrilir. Kaynaklar
  çelişirse ihtiyatlı biçimde üst kademe kazanır.
- **Kitaplığın yine senin:** Kitaplığa bilerek eklediğin seri hiçbir yerde zorla gizlenmez;
  kara liste (yaoi, yuri, futanari, NTR, gore, harem) Vault'ta da geçerli kalmaya devam eder.

### 🔒 Vault: İlk Kullanımda Yetişkin İçerik Onayı

- **Tek seferlik soru:** Vault'u ilk açışında, biyometrik/PIN doğrulamasını geçtikten hemen
  sonra "Vault'ta yetişkin içerik" onayı karşına çıkar. Bu soru bir kez sorulur ve seçimin
  yalnızca bu cihaza kaydedilir.
- **Reddetmek de bir tercih:** "Hayır, gizli kalsın" dersen kasa açılır ama raflar boş
  kalır; giriş her zamanki gibi kilitli kalır. Kararını daha sonra Ayarlar'dan
  değiştirebilirsin.
- **Ayarlar'dan yönet:** Ayarlar → Gizlilik ve Kasa → "Vault'ta yetişkin içeriği göster"
  anahtarı, ilk açılıştaki onayın kararını her zaman değiştirebilir.

### 🧭 Açıklamalı Kurulum Adımları

- **Her soru gerekçesiyle birlikte:** Kurulum adımlarındaki açıklamalar artık neden
  sorulduğunu ve cevabın neyi etkilediğini anlatıyor — okuma dili ana sayfayı, keşfi ve
  aramayı; yaş tercihi kademeleri; dışlama etiketleri akışlarını; favori seriler ise
  önerileri şekillendirir. Türkçe ve İngilizce metinler eş güncellendi.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.2.0` (versionCode `25`) |
| Testler | Tam modül süpürmesi: 172 suit / 1046 test / 0 hata; kabul kapısı `assembleRelease` + `assembleDebug` BUILD SUCCESSFUL, `:app:testReleaseUnitTest` 167/167 (sürüm kaynağında yeşil) |
| Ekli APK | `yomira-v8.2.0.apk` — boyutu ve SHA-256'sı için bu sürüme iliştirilen `SHA256SUMS.txt` (CI, yayın anında eklediği APK için üretir) |
| Doğrulama raporu | `release/VALIDATION_v8.2.0.md` — ham sayılar ve cihaz kontrol listesi |

APK, sürüm commit'i üzerinden repodaki imza anahtarıyla derlenmiştir; imza sertifikası
önceki sürümlerle aynı olduğundan mevcut kurulumların üzerine sorunsuz güncellenir. Yeni
kademeli modelin, Vault onay akışının ve açıklamalı kurulum metinlerinin cihaz üzeri görsel
doğrulaması henüz yapılmadı; madde madde durum [VALIDATION_v8.2.0.md](VALIDATION_v8.2.0.md)
içindeki telefon kontrol listesinde "bekliyor" olarak işaretlidir.
