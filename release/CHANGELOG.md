# Sürüm Geçmişi

Arşivlenmiş sürüm notları. Güncel sürüm için [RELEASE_NOTES.md](RELEASE_NOTES.md).

---

# Yomira v5.2.0-beta

**Saf Mobil Odak, Poster Altı Başlıklar, Hızlı Kapak Önbelleği & İngilizce Kapak Önceliği**

---

### 📱 Saf Android Odak & PC İzolasyonu
- GitHub ana sayfası, dokümantasyon ve derleme kılavuzlarındaki tüm Windows/PC referansları, masaüstü ekran görüntüleri ve indirme linkleri temizlendi.
- Uygulama mimarisi ve kullanıcı deneyimi tamamen Android telefon ve tablet platformuna odaklandı.

### 🖼️ Posterlerin Altında Seri Adı
- Vitrin raflarında (Trendler, Popüler, Size Özel) ve katalog ızgarasında yer alan `MangaCard` bileşenlerine serinin başlığı 2 satırlık, `TextOverflow.Ellipsis` özellikli tipografiyle entegre edildi.

### ⚡ Kapak Yükleme Hızı & 250 MB DiskCache
- `YomiraApp` ImageLoader yapılandırmasına 250 MB özel disk önbelleği eklendi.
- İlk açılışta paralel görsel çözme hızlandırıldı; sonraki açılışlarda kapaklar diskten sıfır gecikmeyle gelir.

### 🌐 İngilizce Kapak Önceliği
- Uygulamada seçilen dil ne olursa olsun, manga ve webtoon kapakları için İngilizce kapaklar (`locale == "en"`) birincil öncelikle seçilir.
- Orijinal Japonca kapaklar yalnızca İngilizce kapak bulunmadığında güvenli fallback olarak gösterilir.

### 📦 Temiz Release Hijyeni
- Sürüm varlıkları staging alanı eski sürümlerden arındırıldı. Release paketine yalnızca `yomira-v5.2.0-beta.apk` ve tekil `SHA256SUMS.txt` dahil edildi.

---

# Yomira v5.0.0

**Modern UI & Webtoon Reader — Cstation vitrin dili, fütüristik Y logosu, yüzen kapsül okuma kontrolleri ve tüm eski sürümlerden tek dokunuşla v5'e yükseltme**

---

### 🎨 Dribbble Cstation & Webtoon Reader tasarım dili

- **Tek OLED Plum paleti:** `#090A0E` tuval, `#13141C` kart, `#9D4EDD → #C084FC` vurgu rampası. Hot pink tamamen kaldırıldı; her eski çağrı lavantaya çözülüyor.
- **Kapsül dili:** arama alanı, alt gezinme, okuma HUD'u ve rozetler aynı `%50` yarıçap ailesini paylaşıyor.
- **Kart ölçeği:** posterler 16dp, paneller 20dp, hero 24dp; 8–28dp arasında ölçeklenen tek köşe sistemi.
- **Numerik HUD:** monospace ve tabular; sayfa sayacı kaymıyor.
- **Cstation vitrin düzeni:** dev poster duvarı (masaüstünde 6–8 sütun), sinematik 16:9 hero, yatay raflar ve devam kartı.

### 🔮 Yeni "Y" master logosu

- Fütüristik **kitap omurgası + sayfa kanatlarını** birleştiren Y monogramı; dört köşeli parıltılar ve neon hale.
- Tek SVG kaynağından üretim: launcher ikonu, adaptive + monochrome katmanlar, Windows `.ico`, uygulama içi marka ve README banner'ı.
- Banner artık ortada amblem, sağında `v5.0` sürüm rozeti taşıyor.

### 🫧 Floating Capsule HUD — başparmak dostu okuma kontrolleri

- Okuma arayüzü ekran kenarına yapışmıyor: üstte bölüm kapsülü, altta ilerleme ve kontrol kapsülleri.
- Telefonda tek elle erişim, masaüstünde fareyle aynı düzen; hiçbir bar sayfanın üzerini kapatmıyor.
- Webtoon / Manga / Comic / Çift mod çipleri, OLED siyah ve ters renkler tek kapsülde.

### 🔄 Otomatik güncelleme bildirimi

- Uygulama açılışta GitHub Releases'ten en son etiketi okur; **yüklü tüm önceki sürümler** (0.1.0-beta → 0.4.1) v5.0.0'dan eski sayılır ve doğrudan yükseltme uyarısı gösterilir.
- Diyalog sürüm notlarını taşır ve imzalı `yomira-v5.0.0.apk` dosyasını indirir.
- Sabit imza keystore'u sayesinde "imzalar uyuşmuyor" hatası yok; üzerine kurulur.
- Ağ yoksa veya API hata dönerse kontrol sessizce geçer — uygulama bloke olmaz.

### 🖥️ Bağımsız Windows Masaüstü & Android Evrensel APK

- Windows için `Yomira-5.0.0.exe` (taşınabilir) ve `Yomira-5.0.0.msi` (kurulum, başlat menüsü kısayolu, yükseltme UUID'si sabit).
- Aynı evrensel APK telefon, tablet ve katlanabilirlerde çalışır; 600dp üzerinde Navigation Rail + 2-Pane otomatik devreye girer.
- Masaüstü seri dosyasına istatistik karoları ve "Benzer seriler" şeridi eklendi.

### 🛠️ Kararlılık ve Kalite

- Gerçek uygulamadan alınan 7 ekran görüntüsü README galerisine eklendi; üretimi `DesktopShowcaseScreensTest` ve debug `ShowcaseActivity` ile tekrarlanabilir.
- Masaüstü webtoon şeridi artık 1.020dp ile sınırlanıyor ve `widthIn`/`fillMaxWidth` sırası düzeltildi (sayfa artık ekranı taşımıyor).
- Android hero carousel yüksekliği sabitlendi: kalan boşluk sayfa göstergesini karttan koparmıyor.
- Detay hero karartması hafifletildi; kapak sanatı telefon ve tablette görünür kalıyor.
- Modern UI metinleri UTF-8 kaynak kodlamasıyla derlenir; Türkçe karakterler tüm platformlarda doğru görünür.

---

### 📦 Kurulum ve İndirme

- **Android APK:** `yomira-v5.0.0.apk`
- **Windows Masaüstü:** `Yomira-5.0.0.exe` & `Yomira-5.0.0.msi`
- **Minimum Sistem:** Android 8.0+ (API 26+) · Windows 10/11
- **Sürüm:** versionCode `6` / versionName `5.0.0`
- **SHA-256 Sağlama:** `SHA256SUMS.txt` ile doğrulanabilir.

### 🌐 Canlı Bağlantılar

- **GitHub Repository:** https://github.com/PaelenRune/Yomira
- **Ürün Tanıtım Sayfası:** https://paelenrune.github.io/Yomira/

#Yomira #v5 #ModernUI #WebtoonReader #Update #Android #Windows

---
---

# Yomira v0.4.1

**Acil Kararlılık & Gömülü Depo Güncellemesi — Samsung Galaxy Açılış Çökmesi Düzeltmesi, Sabit İmza ve Hazır Depo Ekosistemi**

---

### 🛠️ Samsung Galaxy & Android 14/15 Açılış Çökmesi Giderildi
- **AndroidX Startup & Lifecycle Onarımı:** `AndroidManifest.xml` içerisindeki `InitializationProvider` tanımlaması `tools:node="merge"` ile düzeltildi; Jetpack Compose'un ihtiyaç duyduğu `ProcessLifecycleOwner` ve `EmojiCompat` sağlayıcılarının yok edilmesi engellenerek açılışta anında kapanma sorunu tamamen giderildi.
- **Room Database Bütünlük Koruması:** `DatabaseModule.kt` içine `fallbackToDestructiveMigration(dropAllTables = true)` eklendi; olası eski şema kırıntılarının açılışta SQLite `IllegalStateException` fırlatması engellendi.
- **Samsung One UI Recreate Koruması:** Samsung One UI 6 (Android 14) cihazlarda `AppCompatDelegate.setApplicationLocales` çağrısının aktiviteyi açılışta sonsuz yeniden başlatma döngüsüne sokması `syncApplicationLocalesOnStartup` bayrağıyla önlendi.

### 🔑 Sabit İmzalama Keystore'u (Doğrudan Üzerine Güncelleme)
- Repoya kalıcı ve tekrarlanabilir bir imza anahtarı (`yomira_release.keystore`) entegre edildi.
- Hem `debug` hem `release` derlemeleri bu anahtara bağlandı; artık GitHub Actions tarafından üretilen APK'lar her zaman aynı sertifikayı taşır. Kullanıcıların eski sürümü silmesine gerek kalmadan doğrudan "Güncelle" butonuyla üzerine kurulabilir.

### 📦 3 Ana Depo Uygulamaya Gömülü ve Aktif
- **Keiyoushi Universal Deposu**, **Türkçe Çizgi Roman & Manga Deposu** ve **Batı Çizgi Romanları Deposu** uygulamanın ilk kurulumundan itibaren varsayılan olarak aktif ve gömülü gelir.
- Kullanıcı herhangi bir URL girmek zorunda kalmadan binlerce Türkçe manga, manhwa, webtoon ve Marvel/DC serisine ilk andan itibaren doğrudan ulaşabilir.

### 🖥️ Windows Masaüstü (.EXE / .MSI) Dağıtımı
- GitHub Actions üzerinde bağımsız `windows-latest` sunucusu devreye alındı; her sürümde `Yomira-0.4.1.exe` ve `Yomira-0.4.1.msi` otomatik olarak derlenir ve GitHub Releases sayfasına yüklenir.

---

### 📦 Kurulum ve İndirme
- **Android APK:** `yomira-v0.4.1.apk`
- **Windows Masaüstü:** `Yomira-0.4.1.exe` & `Yomira-0.4.1.msi`
- **Minimum Sistem:** Android 8.0+ (API 26+) · Windows 10/11
- **Sürüm:** versionCode `5` / versionName `0.4.1`

---
---

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
