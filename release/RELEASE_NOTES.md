# 🌟 Yomira v8.1.2 — Sürüm Notları

Yomira artık Google Play yolculuğundan bağımsız, doğrudan GitHub üzerinden yayımlanan kişisel bir yapıdır. Bu sürümün ana armağanı: **küratörlü depolar uygulamanın içinde gömülü geliyor**. Kurulum biter bitmez eklenti listesi hazırdır — çevrimdışı bile.

---

### 🚀 Öne Çıkan Yenilikler ve İyileştirmeler

#### 📦 1. Küratörlü Depolar Artık Uygulamanın İçinde (Gömülü)
- **Üç küratörlü indeks APK ile birlikte gelir:** Keiyoushi evrensel deposu (bu sürüm anlık görüntüsünde 1.435 eklenti), Türkçe depolar (93) ve Batı çizgi romanı depoları (20).
- **İlk açılışta hazır:** Temiz kurulumda evrensel depo otomatik kayıtlıdır; eklenti listesi indirme beklenmeden, internetsiz de listelenir. Depo çevrimiçi ulaşılamazsa uygulamanın içindeki anlık görüntü kullanılır — liste boş kalmaz, hata vermez.
- **Türkçe ve Batı depoları isteğe bağlı anahtardır:** Bu kaynaklar zaten evrensel deponun içindedir. Tam listeyi yalnızca Türkçe ya da yalnızca Batı kaynaklarına daraltmak istersen Ayarlar'daki anahtarlarla evrensel depoyu kapatıp dilimi açabilirsin; birlikte açıkken çift kayıt oluşmaz.
- **Kontrol yine sende:** Gömülü depoları anahtarlarla kapatabilir, kendi HTTPS depo adresini ekleyebilirsin. Senin yönettiğin depo listesi hiçbir yükseltmede otomatik değiştirilmez; v8.1.x'ten yükseltirken seçtiğin kaynaklar aynen kalır.
- **Anlık görüntüler repoda yaşar:** `scripts/update-curated-repositories.ps1` ile yenilenebilir; `repositories/turkish/index.min.json` ve `repositories/western/index.min.json` barındırılan kopyaları da bu sürümde yeniden üretildi.

#### 🎛️ 2. Ayarlar ve Eklentiler Düzenlendi
- **"Eklentiler ve depolar" bölümü yeni politikayı anlatıyor:** Bölüm açıklaması, depo anahtarı alt yazıları ("Uygulamada gömülü") ve TR/EN metinler netleştirildi; "Özel depoların" başlığı "Diğer depoların" olarak sadeleşti.
- **Eklentiler → Depoları yönet:** Kayıtlı depolar artık çıplak URL yerine anlaşılır adlarıyla listelenir; küratörlü olanlar **"Gömülü"** rozetiyle işaretlidir, kaldırma düğmesi her satırda durur.
- **Tutarlı bilgilendirme:** Onboarding, boş durum ve güven uyarısı metinleri gömülü depo düzenine göre yenilendi.

#### 🧭 3. Google Play Kısıtları Kalktı, Güvenlik Tercihleri Duruyor
- Play uyumu için uygulanan "deposuz temiz kurulum" politikası kaldırıldı; ilk kurulum deneyimi yeniden zenginleşti.
- Korumalar yerinde: eklenti kurmak ayrıca senin onayındır, ilk kullanımda parmak izi bağlamlı eklenti onayı sorulur ve hiçbir eklenti otomatik yüklenmez.
- Güncellemeler GitHub Releases üzerinden gelir: **Ayarlar → Uygulama Bilgisi → Güncellemeleri Denetle**.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.1.2` (versionCode `22`) |
| Sürüm commit'i | `e8985cb` (tag `v8.1.2`) |
| Testler | 1.113 birim testi, 0 hata (sürüm kaynağında yeşil) |
| Ekli APK | `yomira-v8.1.2.apk` — boyutu ve SHA-256'sı için bu sürüme iliştirilen `SHA256SUMS.txt` (CI, yayın anında eklediği APK için üretir) |
| Temiz kaynak kanıtı | Depodaki `release/yomira-v8.1.2.apk` — 22.813.533 bayt, SHA-256 `694532a63331eedad2386ed0116a3172c5ff1f153a50c5c97a861096f17c9b21` |

APK, sürüm commit'i üzerinden repodaki imza anahtarıyla derlenmiştir; imza sertifikası önceki sürümlerle birebir aynı olduğundan mevcut kurulumların üzerine sorunsuz güncellenir. Depo dizinleri yalnızca metadata taşır; bölüm içeriği veya eklenti APK'sı uygulama içinde barındırılmaz — kurulumlar sandbox'a senin onayınla indirilir.
