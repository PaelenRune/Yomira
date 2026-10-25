# Yomira v0.4.0

**Universal Comic & Manga Ecosystem — Okuma Yolculuğu, Akıllı Panel (Guided View), Windows Masaüstü ve JSON Eklenti Çağı**

---

### 🚀 JSON-First Eklenti Motoru & Mihon/Tachiyomi Yedek İçe Aktarma

- Sistem paket yükleyicisi (`pm install`) ve "Bilinmeyen kaynaklar" güvenlik uyarıları tarihe karıştı; APK'lar Yomira sandbox'ına (`DexClassLoader`) çıkartılır ve hafif JSON fihristi olarak saklanır.
- Tachiyomi ve Mihon `.tachibk` (Protobuf) veya JSON yedek dosyalarını doğrudan içe aktarma; kütüphane, kategoriler ve okuma ilerlemeleri tek dokunuşla aktarılır.

### 🏆 Yomira Reading Journey & Aylık Yomira Wrapped

- Cihaz üzerinde yüzde yüz yerel okuma takip motoru (`ReadingWeight` — 100 sayfalık çizgi roman ile 10 sayfalık manga ağırlığı dengelendi; 60 saniye hareketsizlikte otomatik duran aktif süre sayacı).
- 4 Kategori (Okuma, İstikrar, Keşif, Özel) ve 4 Kademe (Bronz, Gümüş, Altın, Platin) rozet ve başarım sistemi. Seviye & XP ilerlemesi.
- Spotify Wrapped benzeri koyu temalı sinematik aylık ve yıllık okuma özeti slaytları (`WrappedScreen`) ve sosyal medyada paylaşılabilir görsel kart (`WrappedShareCard`).

### 👁️ Akıllı Panel Okuma (Guided View AI) & Çift Sayfa Birleştirme

- Cihaz üzerinde internet gerektirmeden çalışan hafif panel dedektörü (`GuidedViewDetector`); panelden panele sinematik akış ve konuşma balonlarına net odaklanma.
- Çift Sayfa Geniş Sahne Birleştirici (`SpreadStitcher`): İki sayfaya bölünmüş büyük çizimleri tespit edip sıfır dikişle yan yana birleştirir.
- 120Hz ProMotion uyumlu webtoon seam sökücü ve donanım bitmap geri dönüşüm havuzu (`HardwareBitmapPool`).
- Spoiler-Free AI Bölüm Hatırlatıcı (`SpoilerFreeRecapEngine`): Uzun ara verilen serilere dönüşte yalnızca okunan son bölüme kadar olan olayları ve karakterleri özetler, asla gelecekten spoiler vermez.

### 🖥️ Bağımsız Windows Masaüstü Uygulaması (.EXE / .MSI) & Tablet 2-Pane UI

- Compose Multiplatform ile geliştirilen bağımsız Windows masaüstü sürümü (`desktop/`): 2K/4K monitörler için 6-8 sütunlu poster ızgarası, F11 tam ekran modu, yön tuşları (`←`/`→`) ve fare tekerleğiyle akıcı webtoon kaydırma.
- Tablet ve katlanabilir ekranlar için dinamik 2-Pane adaptif düzen ve sol Yan Gezinme Rayı (Navigation Rail).

### 🛡️ Gizlilik & Kamuya Açık Vitrin Hijyeni

- Onboarding sadeleştirildi (TR/EN dil tercihi + topluluk depoları). Yetişkin içerik sorusu baştan kaldırıldı; Kasa varsayılan kapalı (`stealth mode`), yalnızca Ayarlar'dan PIN ile açılabilir.
- Kamuya açık alanda yüzde yüz güvenli ve saygın vitrin.

---

### 📦 Kurulum ve İndirme

- **Android APK:** `yomira-v0.4.0.apk` (GitHub Releases üzerinden indirilebilir)
- **Windows Masaüstü:** `Yomira-0.4.0-x64.exe` / `.msi` (GitHub Releases üzerinden indirilebilir)
- **Minimum Sistem:** Android 8.0+ (API 26+) · Windows 10 ve üzeri
- **SHA-256 Sağlama:** `SHA256SUMS.txt` ile doğrulanabilir.
- **Sürüm:** versionCode `4` / versionName `0.4.0`

### 🌐 Canlı Bağlantılar

- **GitHub Repository:** https://github.com/PaelenRune/Yomira
- **Ürün Tanıtım Sayfası:** https://paelenrune.github.io/Yomira/

---
---

# Yomira v0.3.0-beta

**Universal Comic & Manga Ecosystem — Keşif, Filtreleme ve Kişiselleştirme Güncellemesi**

---

### 🌟 Öne Çıkan Yenilikler

1. **Sonsuz Kaydırma (Infinite Scroll):**
   - Ana Sayfa, Keşfet ve +18 Vault ızgaraları artık sayfa sonuna yaklaşıldığında otomatik olarak yeni içerik yükler.
   - AniList GraphQL, MangaDex, ComicVine ve OpenLibrary kaynaklarından paralel sayfalama (`PagedCatalogLoader`); bir kaynak hata verse bile diğerleri ızgarayı doldurmaya devam eder.
   - Tekrarlı istekler engellenir, tükenen akışta "Tüm seriler yüklendi" durumu gösterilir; neon nabız göstergesiyle kesintisiz yükleme hissi.
   - Vault'un kendi bağımsız sayfalama imleci vardır ve hiçbir zaman genel akışla karışmaz.

2. **42 Tag Matrisi (Çoklu Etiket Filtresi):**
   - 42 etiketli kapsamlı filtreleme matrisi: Action, Adventure, Comedy, Drama, Ecchi, Fantasy, Horror, Isekai, Mystery, Romance, Sci-Fi, Slice of Life, Sports, Supernatural, Thriller, Psychological, Harem, Mature, Nudity, Adult Cast, Gore, Seinen, Shounen, Shoujo, Josei, Kids, School, Military, Mecha, Music, Cooking, Historical, Vampire, Demons, Magic, Martial Arts, Super Power, Video Game, Webtoon, Western, Gender Bender, Award Winning.
   - Çoklu seçim AND mantığıyla çalışır: `Ecchi + Komedi` seçimi yalnızca her iki etiketi taşıyan serileri getirir.
   - Etiketler yerelleştirilmiştir (TR/EN) ve Türkçe/İngilizce sağlayıcı tür adlarını (Aksiyon/Action, Bilim Kurgu/Sci-Fi vb.) aynı anda eşler.

3. **Batı Çizgi Romanları Dünyası (Western Comics):**
   - Ana sayfada **"🇺🇸 Amerikan & Batı Çizgi Roman Dünyası"** özel vitrin rayı: Marvel, DC, Image, Dark Horse, Vertigo, Skybound, IDW ve daha fazlası.
   - Spider-Man, Batman, Superman, X-Men, Invincible, The Boys, Watchmen, Saga, Hellboy gibi marka seriler öncelikli sıralanır.
   - ComicVine ve OpenLibrary entegrasyonu ile çevrimiçi keşif; paket içi katalogla çevrimdışı da doludur.
   - Türkçe ve Batı çizgi romanları için imzalı topluluk depo indeksleri (`repositories/turkish`, `repositories/western`).
   - Yanlış sınıflandırmaya karşı korumalı eşleştirme: `Saga` gibi ortak kelimeler yalnızca tam başlıkta eşleşir; `Vinland Saga` manga rafında kalır.

4. **Kanonik Tekilleştirme (Canonical Deduplication):**
   - `CatalogMerger` ve `CatalogRecordMapper`, aynı serinin farklı kaynaklardan gelen kopyalarını (romaji/İngilizce/Türkçe başlık) tek bir kanonik kayıtta birleştirir.
   - Kaynak kimliği + normalize başlık + alternatif başlık eşleşmesiyle güncelleme; kopya kart oluşmaz.
   - Sonsuz kaydırma, canlı arama ve trend beslemesi aynı birleştirme hattından geçer.

5. **Çift Dilli Arayüz (Tam EN/TR i18n):**
   - 185 string kaynağının tamamı İngilizce (`values/`) ve Türkçe (`values-tr/`) olarak eşlendi.
   - Uygulama içi dil değiştirici ve Ayarlar ekranından anında geçiş; seçim yeniden başlatmada korunur.
   - Android 13+ sistem dil kısayolu (`localeConfig`) ve 33 altı sürümler için AppCompat otomatik yerel ayar depolama.
   - Katalog, okuyucu, detay, kasa, onboarding ve güncelleme diyaloğu dahil tüm yüzeyler çift dilli.

6. **Yeni Ayarlar Ekranı:**
   - OLED uyumlu, bölümlere ayrılmış yeni ayarlar merkezi.
   - Gizlilik ve +18 Vault anahtarı, dil seçimi, eklenti depoları (Keiyoushi / Türkçe / Batı), özel depo URL'si, önbellek temizleme ve manuel güncelleme denetimi.
   - Sürüm bilgisi ve tek dokunuşla güncelleme kontrolü.

7. **Gizlenebilir Vault (Stealth Mode):**
   - Vault sekmesi tek anahtarla arayüzden **tamamen kaldırılabilir**; kilitli sekme olarak bile görünmez.
   - Tercih kalıcıdır; kapatıldığında kasa otomatik olarak kilitlenir.
   - `FLAG_SECURE` ile son uygulamalar önizlemesinde hassas içerik sızıntısı engellenir; panik butonu anında ana sayfaya döner.

8. **Yeni Neon İkon:**
   - Yeni adaptif launcher ikonu: mor neon elmas portal, fuşya yörünge yayı ve beyaz "Y" kitap sayfaları.
   - `mipmap-anydpi-v26` adaptif ikon + yuvarlak varyant; koyu OLED arka planla uyumlu.

---

### 🐛 İyileştirmeler ve Kalite

- Katalog bileşenleri için ortak `CatalogCopy` / `UiCopy` katmanı; modüller string kaynağı tutmaz.
- 42 etiket için birim testleri (`CatalogTagsTest`) ve kanonik birleştirme testleri (`CatalogMergerTest`).
- Sonsuz kaydırma yüklenirken çift istek koruması ve hata durumunda "Yeniden dene" akışı.
- Eklenti deposu indeksleri için `scripts/update-curated-repositories.ps1` doğrulama betiği.

---

### 📦 Kurulum ve İndirme

- **Doğrudan APK:** `yomira-v0.3.0-beta.apk` (GitHub Releases üzerinden indirilebilir)
- **Minimum Sistem:** Android 8.0+ (API 26+)
- **SHA-256 Sağlama:** `SHA256SUMS.txt` ile doğrulanabilir.
- **Sürüm:** versionCode `3` / versionName `0.3.0-beta`

### 🌐 Canlı Bağlantılar
- **GitHub Repository:** https://github.com/PaelenRune/Yomira
- **Ürün Tanıtım Sayfası:** https://paelenrune.github.io/Yomira/
