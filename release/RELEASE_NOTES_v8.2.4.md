# 🌟 Yomira v8.2.4 — Sürüm Notları

Bu sürüm aramanın "anlamasını" getirir: **seri adı artık hangi dilde veya hangi
yazımla yazılırsa yazılsın tanınıyor.** Japonca özgün ad, İngilizce ad, romaji ve yazım
varyantları tek bir eşleştirme motorunda buluşuyor. Buna ek olarak eklenti logolarının
baş-harf rozeti artık her cihazda görünür oluyor ve kitaplık araması okuduğunuz ama
kaydetmediğiniz serileri de buluyor.

> **TR:** v8.2.4, birleşik eşleştirme motoruyla aramanın Japonca/özgün-ad/yazım-varyantı
> sorguları tanımasını sağlar (NARUTO=ナルト, 呪術廻戦=Jujutsu Kaisen); sağlayıcıların
> bildirdiği alternatif adlar otomatik olarak bir alias sözlüğüne işlenir. Eklenti
> logosu yüklenemezse baş-harf rozeti artık her cihazda görünür (erişilebilirlik
> düzeltmesi) ve kitaplık araması okunan-ama-kayıtlı-olmayan serileri de bulur.
>
> **EN:** v8.2.4 gives search a unified matcher engine that understands Japanese,
> original-title and spelling-variant queries (NARUTO=ナルト, 呪術廻戦=Jujutsu Kaisen);
> alternative names reported by providers are automatically folded into an alias
> dictionary. When an extension logo cannot load, the initial-letters badge is now
> visible on every device (accessibility fix), and library search also finds series
> you read but never saved.

---

### 🧭 Birleşik Eşleştirme Motoru (Normalizasyon + Alias Sözlüğü + Fan-out)

- **Japonca ve özgün adlar:** Aramada `ナルト` yazmak NARUTO'yu, `呪術廻戦` yazmak
  Jujutsu Kaisen'i bulur. Katakan ve hiragan aynı biçime katlanır, Latin diyakritikleri
  (`Tokyo Bağlı`→`Tokyo Bagli` gibi) temizlenir; iki taraf da aynı kurallardan geçtiği
  için karşılaştırma hangi dilde yazıldığını bilmeden eşleşir.
- **Yazım-varyantı ve sezon-soneki toleransı:** Romaji yazımları (`ou`↔`o`, `uu`↔`u`,
  `sh`↔`sy`), noktalama ve parantezli niteleyiciler (`(official)`, `(manhwa)`), editoryal
  sonekler (`Full Color Edition`) ve sezon/bölüm işaretleri (`Part 6`, `2nd Season`,
  `Act II`, sonundaki `III` gibi devam numaraları) aynı biçime katlanır — `Shingeki no
  Kyojin 2. Sezon` ile `Attack on Titan Second Season` aynı seriye çözümlenir.
- **Otomatik alias sözlüğü:** Metadata sağlayıcıların seri için bildirdiği her alternatif
  ad (kanonik, İngilizce, yerelleştirilmiş) artık veritabanındaki yeni `series_alias`
  tablosuna yazılır ve sonraki aramalar bu adlarla da seriye ulaşır (veritabanı sürüm
  6→7 otomatik göçle geçilir). Sözlük yalnızca eşleştirme içindir; rafta görünen adı
  asla değiştirmez.
- **Akıllı sorgu fan-out:** Canlı arama, uzak kaynakların indekslemiş olması muhtemel
  en fazla dört yazımı (macron-açık romaji, noktalamasız biçim, tam katlanmış biçim)
  türetir; ilk anlamlı sonuçla durur, böylece her deneme bir ağ turu olan sorgular
  sınırlı kalır.
- **Ölçülen kazanım:** 100 serilik kıyaset üzerinde 412 sorgunun tamamı (EN + özgün ad
  + alias) yeni motorla doğru seriye bağlandı — eski motorda bu oran %93,7 idi; "zor"
  işaretli sorgularda %100. Ayrıntılar
  [VALIDATION_v8.2.4.md](VALIDATION_v8.2.4.md) içindeki benchmark bölümünde.

---

### 🖼️ Eklenti Rozeti Erişilebilirlik Düzeltmesi

- **Baş-harf rozeti artık her cihazda görünür:** Eklenti logosu (ikon → favicon → baş
  harfler) zincirinde son adım olan baş-harf rozeti, önceki sürümde erişilebilirlik
  ağacından yanlışlıkla budanıyordu: rozet ekrana çizilse de ekran okuyucular ve UI
  dökümü onu göremiyordu. v8.2.4'te budanan anlam düzeltildi; logo yüklenemezse baş
  harfler her cihazda, TalkBack ve UI dökümleri dahil, görünür durumda.
- **Sapkma-dirençli durum makinesi:** Yükleme hatası zinciri saf bir durum makinesine
  çıkarıldı: hata sırasında adaylar sırayla denenir, liste tükenince baş harflerde
  kalınır ve yükleme başardıktan sonra gelen fazladan bir hata görüntüyü yıkamaz.

---

### 📚 Kitaplık Araması Okunan Serileri de Bulur

- **Okunan-ama-kaydedilmemiş seriler:** Kitaplık sekmesinde arama artık yalnızca
  bilinçli olarak kaydettiğiniz serilerde değil, okuma ilerlemesi olan tüm serilerde
  arar. Örneğin devam kartından okuduğunuz `One Piece` daha Kitaplık'a eklemediyseniz,
  Kitaplık'ta "one piece" yazmak artık onu bulur — boş sonuç ekranı, aynı ekrandaki
  devam kartıyla çelişmez. Boş sorgudaki raf yine yalnızca kayıtlı serileri gösterir.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.2.4` (versionCode `28`) |
| Testler | Kabul kapıları sürüm kaynağında yeşil: `:app:testReleaseUnitTest` **192/192**, `:domain:extension:test` **88/88** (benchmark dahil) ve `assembleRelease` başarılı. Cihaz turundan gelen düzeltmelerin (FIX-M2) modül kapıları — app **192**, feature-home **38**, data-metadata **43**, core-network **51** test, 0 başarısız — ve merge-sonrası ana ağaç doğrulaması (app 192, domain/extension 88, feature-home 38, data-metadata 50, core-network 51; hepsi 0 başarısız) tamamen yeşil. Bu sürümde **73 yeni birim testi + 1 yeni androidTest (Room göçü)** |
| Benchmark | 100 serilik kıyaset: **412 sorgu**; eski motor **%93,7** → birleşik motor **%100,0**, zor küme **%100,0** — %90 kabul kapısı GEÇTİ |
| Ekli APK | `yomira-v8.2.4.apk` — boyutu ve SHA-256'sı için bu sürüme iliştirilen `SHA256SUMS.txt` (CI, yayın anında eklediği APK için üretir) |
| Doğrulama raporu | `release/VALIDATION_v8.2.4.md` — benchmark ham satırları, test sayıları, commit envanteri ve cihaz kontrol listesi |

APK, sürüm commit'i üzerinden repodaki imza anahtarıyla derlenmiştir; imza sertifikası
önceki sürümlerle aynı olduğundan mevcut kurulumların üzerine sorunsuz güncellenir.
Bu sürümün cihaz üzeri doğrulaması henüz yapılmadı; orijinal-ad araması, baş-harf
rozeti görünürlüğü ve `One Piece` kitaplık araması maddeleri
[VALIDATION_v8.2.4.md](VALIDATION_v8.2.4.md) içindeki telefon kontrol listesinde
"bekliyor" olarak işaretlidir. Uyarlama (Anime/Donghua/K-Drama) rozeti ise sağlayıcı
sinyali yetersiz kaldığı için VERİ-KISITI olarak işaretlenmiştir; ayrıntı ve kanıt
zinciri aynı rapordadır.
