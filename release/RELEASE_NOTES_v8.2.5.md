# 🌟 Yomira v8.2.5 — Sürüm Notları

Bu sürüm keşif listelerinde "kayıp" görünen serileri geri getirir: **+18 temalı ama
gerçekten yasak olmayan seriler artık normal keşifte görünür.** Kara liste bu sürümle
iki kademeli çalışıyor — gerçekten yasak kategoriler her koşulda gizlenirken, tarz
etiketleri (harem, NTR, gore vb.) içeriği listeden silmek yerine yalnızca sıralamada
aşağı düşürüyor. Vault rafları da belirgin biçimde daha dolu: daha derin tarama,
iki-varyantlı raf sorguları ve "gerçekten boş" ile "yavaş doldu" ayrımı.

> **TR:** v8.2.5'te kara liste iki kademeli ayrıldı: gerçekten yasak kategoriler
> (futanari, incest, bestiality, rape, lolicon, shotacon) tercihlere, yaşa ve +18
> onayına bakılmaksızın her yüzeyde gizli kalır; tarz etiketleri (harem, NTR, gore,
> ecchi) ise seriyi listeden silmez, yalnızca sıralamada düşürür. Böylece daha önce
> "kaybolan" +18 temalı seriler normal keşfe döner; tek bir yanlış tür etiketi de bir
> seriyi artık gizlemez. Vault rafları daha derin taramayla (sayfa başına 25 → 50),
> iki-varyantlı başlık aramalarıyla ve "gerçekten boş" ayrımıyla daha dolu görünür.
>
> **EN:** v8.2.5 makes the blacklist two-tier: truly banned categories (futanari,
> incest, bestiality, rape, lolicon, shotacon) stay hidden on every surface regardless
> of preferences, age or adult consent, while style tags (harem, NTR, gore, ecchi) no
> longer remove a series — they only rank it lower. "+18 themed but not banned" series
> return to normal discovery, and a single wrong genre tag no longer hides a series.
> Vault shelves look fuller thanks to a deeper scan (25 → 50 per page), two-variant
> title-based rail queries, and a clear "truly empty" vs "slowly filling" distinction.

---

### 🛡️ İki-Kademe Kara Liste (Gerçek Yasak + Tarz Tercihi)

- **"+18 temalı" ≠ "yasak":** Eski davranışta harem, NTR, gore gibi tarz etiketleri
  taşıyan seriler kara listeye alınınca keşiften, aramadan ve detaydan tamamen
  siliniyordu — sağlayıcıların gevşek etiketleri yüzünden çok sayıda sıradan seri
  "kayboluyordu". Artık bu tarz etiketleri **yasak listesine ekleseniz bile seri
  görünmeye devam eder**; yalnızca sıralamada aşağı iner (her eşleşen tarz etiketi
  küçük bir sıralama cezası ekler). Seriyi rafta bulur, açar, okumaya devam edersiniz.
- **Gerçekten yasak kategoriler her koşulda gizli:** Futanari, incest, bestiality,
  rape/non-con, lolicon ve shotacon etiketli içerik; tercihlerinize, yaşınıza, +18
  onayınıza veya Vault kilidinize bakılmaksızın **hiçbir yüzeyde görünmez** (keşif,
  arama, seri detayı, Vault dahil). Bu kademeye kullanıcı tercihi karışmaz.
- **Diğer tür tercihleriniz eskisi gibi:** Yaoi/yuri gibi klasik tür tercihleri
  davranış değişikliğine uğramadı — seçtiğinizde gizlemeye devam eder.
- **Tek yanlış etiket artık seri gizlemez:** Etiket eşleştirmesi "içeriyor" yerine
  normalize edilmiş **tam eşleşmeye** (+ yazım sözlüğü: netorare, guro, ecchi/etchi,
  non-con, lolicon…) çevrildi. Bir serinin tür etiketlerinden yalnızca biri yanlışlıkla
  yasaklı bir tarz taşıyorsa seri artık görünmez olmaz.

---

### 📚 Vault Rafları Daha Dolu

- **Daha derin tarama:** Vault beslemesi her kaynaktan sayfa başına **50 sonuç**
  çekiyor (eskiden 25). Normal keşif akışı 25'te kalır; yalnızca +18 Vault taraması
  derinleşir.
- **İki-varyantlı raf sorguları:** İlk Vault sayfasında her raf, başlık-tabanlı
  aramayla **ikişer yazım varyantıyla** taranır ve sonuçlar birleştirilir:
  "adult webtoon" + "pornhwa", "hentai" + "erotic manga", "doujinshi" + "doujin",
  "erotic comic" + "3d comic". Sağlayıcılar OR işlecini desteklemediği için varyantlar
  ayrı sorgular halinde gönderilir; yalnızca ilk sayfada çalıştığından normal sayfalama
  aynı aramaları tekrarlamaz.
- **"Gerçekten boş" ayrımı:** Üç ve altı serili rafların üstünde artık "Bu raf yavaş
  doldu; daha fazla sonuç için kaynakları eşitle." ipucu görünür. Tam boş mesajı ise
  yalnızca raf **gerçekten** boşken gösterilir — "daha fazla yükleniyor" veya sayfalama
  hatası sırasında yanlışlıkla boş ekran çıkmaz.
- **Raf teşhisi:** Vault açıkken her kaynak ve raf için aşama sayıları (giren / açık
  içerik dışı / elenen / eşleşmeyen / eşleşen) `VaultDiagnostics` günlük satırlarına
  yazılır — başlık veya kimlik içermez, yalnızca sayı.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.2.5` (versionCode `29`) |
| Testler | Kabul kapıları sürüm kaynağında yeşil: `:app:testReleaseUnitTest` **192/192** ve `assembleRelease` başarılı. Merge-sonrası ana ağaç doğrulaması — app **192**, core/model **30**, feature-home **39**, feature-vault **27** test, 0 başarısız (toplam **288**); dal worktree kanıtları — core/model **30**, feature-home **32**, feature-vault **27**, app **167** test (toplam **256**), hepsi yeşil. Bu sürümde **7 yeni birim testi + 2 sınanmış testin güçlendirilmesi** |
| Ekli APK | `yomira-v8.2.5.apk` — boyutu ve SHA-256'sı için bu sürüme iliştirilen `SHA256SUMS.txt` (CI, yayın anında eklediği APK için üretir) |
| Doğrulama raporu | `release/VALIDATION_v825.md` — merge kanıtı, test ham sayıları, commit envanteri ve cihaz kontrol listesi |

APK, sürüm commit'i üzerinden repodaki imza anahtarıyla derlenmiştir; imza sertifikası
önceki sürümlerle aynı olduğundan mevcut kurulumların üzerine sorunsuz güncellenir. Bu
sürümün cihaz üzeri doğrulaması henüz yapılmadı; Vault raf doluluk gözlemi, MangaDex
başlık-tabanlı varyant limiti notu ve EXPLICIT sızıntı taraması maddeleri
[VALIDATION_v825.md](VALIDATION_v825.md) içindeki telefon kontrol listesinde
"bekliyor" olarak işaretlidir.
