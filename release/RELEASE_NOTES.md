# Yomira v0.2.0-beta

**Universal Comic & Manga Ecosystem — Büyük Güncelleme & Katalog Lansmanı**

---

### 🌟 Öne Çıkan Yenilikler

1. **İlk Kurulum Sihirbazı (Onboarding Wizard UI):**
   - Uygulama ilk açıldığında kullanıcıyı karşılayan 3 adımlı şık OLED sihirbazı.
   - Adım 1: Okuma Dili Tercihi (Türkçe [TR] / English [EN]).
   - Adım 2: Yetişkin İçerik İzni (+18 Açık/Kapalı) — Hikaye odaklı Level 1 Mature ile saf Level 2 Explicit arasındaki farkı açıklar.
   - Adım 3: Keiyoushi Resmi Depo Bağlantısı (Tek tıkla eklenti deposunu hazırlama).
   - Tercihler kalıcı olarak saklanır; sonraki açılışlarda doğrudan ana akışa geçilir.

2. **Asla Boş Kalmayan Evrensel Katalog (159 Seri Dahili Tohum):**
   - Eklenti veya internet kurulu olmasa dahi vitrinler asla boş kalmaz!
   - `assets/prepopulated_catalog.json` içinde 159 popüler seri:
     - *Mainstream:* Solo Leveling, One Piece, Jujutsu Kaisen, Chainsaw Man, Berserk, Attack on Titan, Bleach, Naruto...
     - *Webtoon/Manhwa:* Omniscient Reader, The Beginning After The End, Tower of God, Nano Machine...
     - *Western Comics:* Spider-Man, Batman, Saga, Invincible, The Boys, Watchmen...
     - *Level 1 (MATURE / ECCHI):* "I Formed a MILF Party with the Heroes' Mothers", "Kimi wa Midara na Boku no Joou", "Prison School", "Gantz" (Ana sayfada 16+ rozetiyle).
     - *Level 2 (EXPLICIT):* "Silent War", "Boarding Diary", "Secret Class" (Yalnızca +18 Vault için).
   - Gerçek AniList / MangaDex CDN afişleri, yazarlar, özetler ve puanlar.

3. **Canlı Keşif ve Otomatik Trend Beslemesi (Waterfall Discovery):**
   - Uygulama açılışında arka planda AniList GraphQL ve MangaDex'ten güncel trend serileri çekilir ve Room önbelleğine eklenir.
   - Canlı Arama Şelalesi: Yerel veritabanında bulunamayan seriler Baka-Updates ve AniList şelalesinden aranıp anında yerel kataloğa kaydedilir.

4. **Uygulama İçi Otomatik Güncelleme Bildirimi (In-App Auto-Updater):**
   - GitHub Releases API entegrasyonu.
   - Yeni bir sürüm çıktığında ekranda OLED bildirim diyaloğu (`UpdateDialog.kt`) belirir.
   - Değişiklik notlarını gösterir ve doğrudan tek tıkla APK güncellemesi sunar.

5. **Dribbble OLED UI & Evrensel Okuyucu Motoru:**
   - 2, 3, 4 Sütun Izgara Seçici.
   - Webtoon kesintisiz dikey kaydırma, Manga (RTL) ve Western (LTR) sayfa çevirme motoru.
   - Smart Zoom (çift dokunuş odaklama), OLED Gece Modu ve Ters Renkler filtresi.
   - Bağımsız +18 Kasa, PIN kilidi ve tek tıkla oturumu kapatan Panik Butonu.

---

### 📦 Kurulum ve İndirme

- **Doğrudan APK:** `yomira-v0.2.0-beta.apk` (GitHub Releases üzerinden indirilebilir)
- **Minimum Sistem:** Android 8.0+ (API 26+)
- **SHA-256 Sağlama:** `SHA256SUMS.txt` ile doğrulanabilir.

### 🌐 Canlı Bağlantılar
- **GitHub Repository:** https://github.com/PaelenRune/Yomira
- **Ürün Tanıtım Sayfası:** https://paelenrune.github.io/Yomira/
