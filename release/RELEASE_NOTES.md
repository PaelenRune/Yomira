# 🌟 Yomira v8.1.3 — Sürüm Notları

Bu sürüm rafların gerçek okuyucu ortaklaşımına (popülerlik) dayanmasını sağlar:
Ana sayfa, Keşfet ve Vault raflarında artık gerçekten popüler seriler öne çıkar.

---

### 🧮 Raflar Artık Gerçek Popülerlikle Sıralanıyor

- **AniList keşfi popülerlik sırasına geçti:** Öne çıkan raf artık sezonluk trendler
  yerine kalıcı popülerlik sıralamasını (POPULARITY_DESC) kullanır.
- **Çevrimdışı katalog gerçek sayılarla geldi:** Uygulama içi 159 serilik başlangıç
  kataloğuna gerçek okuyucu sayıları eklendi (Chainsaw Man, Solo Leveling, Berserk,
  One Piece…). Böylece ilk kurulumda bile popüler raflar internetsiz doğru çalışır.
- **Keşif rafı consenso ile sıralanır:** Yeni indirilen seriler kendi entre
  popularity değerine göre sıralanır; yeni diye sıradan bir seri zirveye çıkamaz.
- **Vault tazelik bonusi küçüldü:** Vault raflarında "yeni geldi" bonusu geriletilerek
  gerçek popülerliğin önüne geçmesi engellendi.

### 🔧 Teknik

- `prepopulated_catalog.json` artık `popularity` alanı taşır; tohumlama bu değeri
  Room'a yazar (şema değişikliği yok).
- Home keşif rafı ve Vault rail sıralaması `globalRankScore` üzerinden tutarlı.

---

### 📦 Sürüm ve Doğrulama Bilgileri

| Bilgi | Değer |
| --- | --- |
| Sürüm | `8.1.3` (versionCode `23`) |
| Testler | Tüm modüllerde yeşil (sürüm kaynağında) |
| Ekli APK | `yomira-v8.1.3.apk` — boyutu ve SHA-256'sı için bu sürüme iliştirilen `SHA256SUMS.txt` (CI, yayın anında eklediği APK için üretir) |
| Temiz kaynak kanıtı | Depodaki `release/yomira-v8.1.3.apk` — boyut ve SHA-256 `SHA256SUMS.txt` içinde |

APK, sürüm commit'i üzerinden repodaki imza anahtarıyla derlenmiştir; imza sertifikası
önceki sürümlerle aynı olduğundan mevcut kurulumların üzerine sorunsuz güncellenir.
