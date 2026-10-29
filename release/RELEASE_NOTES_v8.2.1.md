# 🌟 Yomira v8.2.1 — Sürüm Notları

Bu sürüm kütüphanesindeki her seriyi tek bir kimlikte buluşturur: **adlar ve kapaklar
artık her yerde aynı kurala uyuyor — İngilizce ad öncelikli, yoksa orijinal dil.** Seri
detayındaki uyarlama rozetleri kalıcı oldu, Ayarlar'a tek dosyalık yedek alıp geri
yüklemesi geldi; kurulumdaki depo adımı ve okuyucudaki yeniden deneme akışı düzeltildi.

> **TR:** v8.2.1 seri detayında Anime/K-Drama/Donghua gibi uyarlama rozetlerini kalıcı
> yapar; ad ve kapaklarda İngilizce-öncelikli tek kuralı uygular ve eski kitaplığı
> otomatik tazeler; Ayarlar'a `.tachibk` yedek oluştur/geri yükle getirir; kurulum
> depo adımını ve okuyucuda tek dokunuş yeniden denemeyi düzeltir.
>
> **EN:** v8.2.1 makes adaptation badges (Anime/K-Drama/Donghua…) permanent on series
> detail, applies one EN-first rule to titles and covers everywhere while auto-refreshing
> the existing library, adds create/restore `.tachibk` backups to Settings, and fixes the
> onboarding repository step plus one-tap retry in the reader.

---

### 🎬 Uyarlama Rozetleri Kalıcı

- **Seri detayında hangi uyarlamalar olduğu görünür:** Bir serinin Anime, K-Drama,
  Donghua, OVA gibi uyarlamaları varsa rozetleri seri detayının üst bilgi bloğunda,
  kademe rozetinin yanında artık kalıcı olarak durur; içeriğe göre gelip gitmez.
- **Kilit korunur:** Vault kilitliyken kapalı (explicit) uyarlamalar detay sayfasına
  sızamaz; rozetler yalnızca senin görmeye yetkili olduğun bilgiyi gösterir.

### 🏷️ Her Yerde Aynı Ad, Aynı Kapak

- **İngilizce ad öncelikli:** Kitaplık, akışlar, keşif ve seri detayı artık aynı seriyi
  her yerde aynı adla anar: sağlayıcının İngilizce adı varsa o, yoksa orijinal dildeki
  ad (Japonca, Korece, Çince ya da romanize biçim) kullanılır. Sağlayıcı sırası değişse
  bile raf adı artık değişmez.
- **Kapaklar da aynı kurala uyar:** İngilizce yerelleştirilmiş kapak varsa o, yoksa
  orijinal dildeki kapak seçilir. MangaDex'te artık öncelik orijinal dildeki kapağa
  verilir; kimlik eşleşmesi sağlanmamış kayıtların kapakları etiketleri etkilemez.
- **Eski kitaplık otomatik tazelenir:** v8.2.1'e ilk girişinde mevcut kitaplığın ad ve
  kapakları yeni kurala göre arka planda yeniden çözümlenir. Bu tazeleme yalnızca
  ölçülmeyen ağda, ekran açık ve kilitli değilken çalışır; veri tasarrufunda duraklar.
  İstediğin an Ayarlar → Depolama'dan "Kitaplık adlarını ve kapaklarını yenile" ile elle
  de tetikleyebilir, ilerleme görünürken iptal edebilirsin.

### 💾 Yedek Oluştur / Yedek Geri Yükle

- **Tek dosya, her yerde:** Ayarlar → Depolama'ya "Yedek oluştur" ve "Yedek geri yükle
  (.tachibk)" geldi. Yedek dosyası `yomira-backup-YYYYMMDD-HHmm.tachibk` adıyla,
  seçtiğin klasöre kaydedilir (SAF ile).
- **Kapsam net:** Yedek, kitaplığını ve okuma geçmişini kapsar; indirilen sayfalar ve
  güvenlik ayarları dahil değildir. Dosya şifrelenmez ve Vault başlıklarını içerebilir —
  uygulama bunu yedek alırken sana açıkça söyler, dosyayı özel bir yerde sakla.
- **Temiz kuruluma geri yükleme:** Yeni ya da boş bir kurulumda yedeği geri yüklediğinde
  ad/kapak, içerik kademesi (SAFE/MATURE/EXPLICIT), favoriler/etiketler ve okuma geçmişi
  korunur. Geri yükleme, uzantılar kurulu olmasa bile kitaplığını olduğu gibi getirir.

### 🧭 Kurulum: Depo Adımı Düzeltildi

- **Ana eylem artık depo eklemek:** Kurulumun son adımında ana düğme "Depo ekle"; "Atla"
  ikincil bir metin düğmesine taşındı. Ana düğme yalnızca onay diyaloğunu açar; iptal
  edersen aynı adımda kalırsın.
- **Çift dokunma koruması:** Depo kaydedilirken ikinci dokunma engellenir; onaydan
  sonra depo bir kez eklenir ve kurulum tamamlanır. Geri/ileri gidersen favori
  seçimlerin korunur. Ayarlardan anketi tekrarlarsan depo adımı bir daha gösterilmez.

### 🔄 Okuyucuda Tek Dokunuş Yeniden Dene

- **"Kaynağa erişilemedi" artık çıkmaz sokak değil:** Kaynak geçici olarak erişilemez
  olduğunda okuyucudaki hata ekranına tek dokunuşla "Yeniden dene" geldi. Ağ düzelince
  aynı yerden devam edersin; çözümleme sürerken düğme kaybolur, tekrar hata alırsan yine
  aynı akış çalışır ve Geri her zamanki gibi işler.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.2.1` (versionCode `26`) |
| Testler | Release Android+JVM tam süpürmesi: **748 test / 0 başarısız / 0 hata / 0 atlanan**; kök test süpürmesi ve `assembleRelease` başarılı; kabul kapısı `:app:testReleaseUnitTest` **182/182** (sürüm kaynağında yeşil). Bu sürümde **36 yeni birim testi + 1 yeni Compose testi** |
| Ekli APK | `yomira-v8.2.1.apk` — boyutu ve SHA-256'sı için bu sürüme iliştirilen `SHA256SUMS.txt` (CI, yayın anında eklediği APK için üretir) |
| Doğrulama raporu | `release/VALIDATION_v8.2.1.md` — ham sayılar, commit envanteri ve cihaz kontrol listesi |

APK, sürüm commit'i üzerinden repodaki imza anahtarıyla derlenmiştir; imza sertifikası
önceki sürümlerle aynı olduğundan mevcut kurulumların üzerine sorunsuz güncellenir.
Uyarlama rozetlerinin, ad/kapak kuralının, yedek akışının, kurulum depo adımının ve
yeniden deneme ekranının cihaz üzeri görsel doğrulaması henüz yapılmadı; madde madde
durum [VALIDATION_v8.2.1.md](VALIDATION_v8.2.1.md) içindeki telefon kontrol listesinde
"bekliyor" olarak işaretlidir.
