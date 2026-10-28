# 🌟 Yomira v8.1.0

Yomira v8.1.0 resmi sürümü yayında! Bu sürüm, fiziksel Xiaomi Mi 10T (`b4bc1593`, Android 12, MIUI 14) cihazında canlı olarak test edilmiş, tespit edilen tüm performans ve kilitlenme (ANR) sorunları tamamen giderilmiş, üçüncü taraf uygulama isimlerinden tamamen arındırılmış saf, orijinal ve yüksek performanslı bir okuma deneyimi sunmaktadır.

---

### 🚀 Öne Çıkan Yenilikler ve İyileştirmeler

#### ⚡ 1. Kapsamlı Performans ve Bellek Optimizasyonları (Sıfır ANR)
- **ContentFilters & Normalizer Hızlandırması:** Kara liste etiketleri ve tür eşleştirme regex'leri statik olarak önceden derlendi; `TAG_ALIAS_LOOKUP` haritası $O(1)$ sabit zamanlı lookup yapısına dönüştürüldü. Ana sayfa vitrin geçişlerindeki 17 saniyelik thread bloklanması ve giriş kilitlenmesi tamamen yok edildi.
- **Extensions Arka Plan Hesaplaması:** Binlerce eklenti ve kaynak satırının UI thread'inde filtrelenmesi ve sıralanması engellendi; hesaplamalar `Dispatchers.Default` üzerine taşındı.
- **PERF-R1 (Okuyucu Bellek Tutumu):** Webtoon ve dikey okuma modunda sayfaların yazılımsal ARGB_8888 bitmap'leri yalnızca Guided View aktifken tutulacak şekilde sınırlandı; 64 MB LRU önbellek taşması giderildi.
- **PERF-N1 (Cloudflare Interceptor Güvenliği):** Turnstile meydan okumalarında OkHttp thread havuzunun kilitlenmesini önlemek için yanıt gövdeleri anında kapatıldı ve 60 saniyelik korumalı zaman aşımı uygulandı.
- **PERF-D1 (Metadata Önbellek Temizliği):** Room veritabanında süresi dolan önbellek kayıtlarının otomatik temizliği (`pruneExpired`) bağlandı; disk şişmesi engellendi.

#### 🎨 2. Kusursuz Arayüz ve Marka Özgünlüğü
- **100% Orijinal Marka Kimliği:** README, dokümantasyon ve kullanıcı arayüzündeki tüm üçüncü taraf uygulama isimleri (Mihon, Nuvio, Dribbble, Tachiyomi, Steam vb.) temizlendi.
- **İndirme Butonlarının Kaldırılması:** Çevrimdışı bölüm indirme kontrolleri tamamen arındırıldı.
- **Okunan Bölüm Karartması:** Okunmuş ve tamamlanmış bölümler şık bir şekilde `%38` opaklığa (`alpha(0.38f)`) karartıldı.
- **4 Adımlı Okuma Anketi:** Dil seçimi (TR/EN), yaş kapısı (18+), kara liste (Yaoi, Yuri, Futanari, NTR, Gore) ve favori türler anketi; Ayarlar ekranından her zaman yeniden başlatılabilir.
- **Başarımlar ve Animasyonlu Bildirimler:** Haptik titreşim ve özel ses tonuyla desteklenen modern başarım bildirim motoru (`YomiraAchievementBanner`).

#### 🛡️ 3. Gizlilik, Güvenlik ve Ağ
- **Gizlilik & Sansür Kalkanı:** Harici VPN gerekmeden Cloudflare DNS-over-HTTPS (DoH) ve Anti-DPI SNI parçalama koruması.
- **Korumalı +18 Vault:** Biyometrik veya cihaz PIN/desen kilidi ile korunan, arka plana geçildiğinde anında kilitlenen gizli kasa sekmesi.
- **Küresel Popülerlik Sıralaması:** Gerçek küresel popülerlik (`item.popularity`), skor ve tazelik dengesi ile çalışan evrimsel ana sayfa vitrini.
- **Multi-Name Romaji Arama:** Romaji ve alternatif Japonca isimlerle anında arama (örn. "Sousou" aramasıyla "Frieren: Beyond Journey's End" tespiti).

---

### 📦 Sürüm ve Doğrulama Bilgileri
- **Versiyon:** `v8.1.0`
- **Sürüm Kodu (versionCode):** `20`
- **Doğrulanan Cihaz:** Xiaomi Mi 10T (`M2007J3SY` / `b4bc1593`), Android 12, MIUI 14
- **Birim Testleri:** 532+ test %100 başarıyla geçti (`testDebugUnitTest`)
- **İmza:** Repo içi resmi anahtar deposu ile imzalanmış evrensel release APK (`app-release.apk`)
