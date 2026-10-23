# Yomira

**v0.1.0-beta · Universal Comic & Manga Ecosystem**

Kotlin ve Jetpack Compose ile yazılmış, manga, manhwa, manhua ve Batı çizgi
romanları için modüler Android okuyucu altyapısı. Tasarım odağı: Dribbble
editoryal vitrinlerinden ilham alan büyük kapaklar, ferah kartlar ve OLED siyahı.

> Bu bir **geliştirici betasıdır**. APK debug anahtarıyla imzalanır; Google
> oturumu ve güvenilir eklenti çalışma ortamı henüz uygulamaya bağlanmamıştır.
> Hazır içerik/kaynak barındırmaz; temiz kurulumda katalog boş olabilir.

## Kurulum

1. Android **8.0 / API 26** veya üzeri cihaz kullanın.
2. Bu depodaki [`release/yomira-v0.1.0-beta.apk`](release/yomira-v0.1.0-beta.apk)
   dosyasını indirin. GitHub Release yayımlanmışsa aynı dosya sürüm varlığı olarak eklenebilir.
3. İndirdiğiniz uygulama için Android'in **Bilinmeyen uygulamaları yükle** iznini
   geçici olarak açın ve APK'yı yükleyin.
4. Vault kullanımı için cihazda güvenli PIN, desen veya parola tanımlayın.

ADB ile kurulum: `adb install -r release/yomira-v0.1.0-beta.apk`.
Başka imzayla yüklenmiş bir paket güncellenemez; kaldırma yerel verileri siler.
Debug imzası üretim dağıtımı için uygun değildir.

## Deneyim ve tasarım

- Sabit OLED siyahı, Material 3 bileşenleri ve edge-to-edge gezinme.
- Ana sayfa ve Vault için ayrı, kalıcı **2 / 3 / 4 sütun seçici**.
- Seri detayları, bölüm listeleri, kitaplık ve yerel arama.
- Yatay/sayfalı okuyucu, webtoon kaydırma ve yakınlaştırma altyapısı;
  ilerleme Room üzerinden saklanır. Sayfa sağlayıcısı ayrıca bağlanmalıdır.
- Vault cihaz kilidiyle açılır; arka plana geçince kapanır. Panik eylemi
  normal ana sayfaya döner. `FLAG_SECURE` ekran yakalama ve Recents önizlemelerini sınırlar.

## Content-Free Core

Yomira **içerik dağıtıcısı değildir**. Uygulamaya manga bölümleri, sayfa görselleri
veya telifli kapak dosyaları gömülmez. Room metadata, uzak URL referansları,
kitaplık ve okuma ilerlemesini tutar; metadata cache bir medya deposu değildir.
Görseller bağlanan kaynaklardan çalışma anında görüntülenebilir. Kullanıcı
yalnızca erişim yetkisine sahip olduğu kaynakları kullanmalıdır.

## Level 0–1–2 içerik ayrıştırma motoru

| Seviye | Sınıf | Yönlendirme |
| --- | --- | --- |
| **0** | SAFE | Genel vitrin; One Piece, Solo Leveling, Spider-Man gibi eserler |
| **1** | MATURE / ECCHI | Normal vitrinde **16+ / Mature** rozeti; hikâye odaklı, hentai olmayan seriler |
| **2** | EXPLICIT | Genel vitrinlerden ayrılır, yalnızca kilitli **+18 Vault** |

`I Formed a MILF Party with the Heroes' Mothers`, `Kimi wa Midara na Boku no
Joou` ve `Prison School` Level 1 olarak tanınır. Hentai, pornografik etiketler ve
açık pornografik sağlayıcı derecelendirmesi Level 2'dir. Bir kaynağın SAFE demesi,
diğer kaynağın açık pornografik kanıtını bastırmaz. Bilinen seri adı bile açık
pornografik kanıtı geçersiz kılamaz. Tek başına “doujinshi” yayın türü
pornografi kanıtı sayılmaz.

Bu motor metadata kanıtlarını sınıflandırır; insan moderasyonunun veya yasal yaş
doğrulamasının yerini almaz. Eksik metadata sınıflandırma doğruluğunu etkiler.

## Waterfall metadata ve Room cache

**BakaUpdates → AniList → MangaDex → Kitsu → ComicVine**

- Öncelikli kaynağın seri kimliği ve dolu alanları korunur; sonraki kaynaklar
  eksik açıklama, kapak referansı, yazar, puan ve yayın durumunu tamamlar.
- Bir sağlayıcının hatası diğerlerini durdurmaz; coroutine iptali yutulmaz.
- Başlık/alternatif başlık eşleşmesi zorunludur; farklı eserlerin ilk arama
  sonuçları körlemesine birleştirilmez. Bilinen farklı yayın yılları birleştirilmez.
- Kaynakların içerik kanıtları birlikte sınıflandırılır. Kitsu kategorileri her
  sonucun kendi ilişkilerinden çözülür; arama sonuçları arasında sızmaz.
- Room metadata cache varsayılan **24 saat** geçerlidir; bozuk cache ağdan
  yenilenir. `forceRefresh` önbelleği atlar; cache hatası ağ sonucunu kaybettirmez.
- ComicVine isteğe bağlı API anahtarı ister. `NetworkConfiguration.comicVineApiKey`
  çalışma anında sağlanmazsa bu adım atlanır. Anahtar APK'ya veya Git'e gömülmez.

Aggregator Hilt üzerinden sağlanır. Bu betadaki vitrin, Room'daki kataloğu okur;
otomatik katalog keşfi/metadata içe aktarma ekranı henüz bağlı değildir.

## Keiyoushi eklenti desteği

Keiyoushi depo indeksini ayrıştırma, eklenti yaşam döngüsü, dosya depoları,
onboarding ve akıllı kaynak eşleştirme katmanları bulunur. Kaynaklar uygulamayla
birlikte gömülü gelmez. Eklenti APK'ları UI sürecinde doğrudan çalıştırılmaz;
`SandboxSourceBridge` güvenilir bir host sınırı tanımlar.

**Beta sınırı:** Android sandbox host/kurulum arayüzü ve `SmartSourceMatcher`
binding'i henüz bağlanmadığından, bu APK'da eklentiyle uçtan uca okuma veya
çevrimdışı indirme hazır değildir. Arayüz bunu açıkça bildirir.

## Google Drive AppData senkronizasyonu

Drive REST v3 istemcisi, `drive.appdata` yetkisiyle gizli `appDataFolder`
üzerinden metadata/kitaplık/okuma ilerlemesini taşıyan JSON senkronizasyonuna
yöneliktir. WorkManager zamanlaması, yerel anlık görüntüler ve çatışma birleştirme
motoru bulunur; bölüm sayfaları veya medya dosyaları senkronize edilmez.

**Beta sınırı:** `SyncAuthModule` henüz token döndürmez; Google oturum açma ve
OAuth izin akışı bağlanana kadar senkronizasyon pasiftir. Statik erişim token'ı
veya istemci sırrı depoya eklenmez. AppData, Drive arayüzünde görünmeyen bir
alan olsa da hassas veriler için uçtan uca şifreleme garantisi değildir.

## Mimari

| Katman | Modüller |
| --- | --- |
| Uygulama | `:app` — Compose gezinme, Hilt bağlantıları |
| Çekirdek | `:core:model`, `:core:database`, `:core:network`, `:core:ui` |
| Domain | `:domain:classifier`, `:domain:extension`, `:domain:sync` |
| Veri | `:data:metadata`, `:data:extension`, `:data:sync` |
| Ekranlar | `:feature:home`, `:feature:vault`, `:feature:details`, `:feature:reader` |

Room migration şemaları `core/database/schemas/` altında sürümlenir.
Yıkıcı migration fallback kullanılmaz. Android otomatik backup kapalıdır;
cleartext HTTP trafiğine izin verilmez.

## Kaynaktan derleme

- JDK **17**, Android SDK Platform **35**, Build Tools **35.0.0**.
- Gradle **8.11.1**, AGP **8.9.2**, Kotlin **2.1.20**, Room **2.7.1**.
- `JAVA_HOME` ve `ANDROID_HOME` ayarlayın veya Android Studio'nun oluşturduğu
  `local.properties` dosyasında `sdk.dir` belirtin. SDK lisanslarını kabul edin.
- Bağımlılık sürümleri: `gradle/libs.versions.toml`.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :domain:classifier:test :core:network:test :data:metadata:testDebugUnitTest
.\gradlew.bat testDebugUnitTest :core:model:test :domain:extension:test :data:extension:test :data:sync:test
.\gradlew.bat :app:lintDebug
New-Item -ItemType Directory -Force release
Copy-Item app/build/outputs/apk/debug/app-debug.apk release/yomira-v0.1.0-beta.apk
```

macOS/Linux üzerinde `sh ./gradlew` kullanın. Windows'ta Türkçe karakter içeren
yollar Gradle test worker'ında sınıf yükleme sorunu oluşturabilir; ASCII karakterli
bir checkout/derleme yolu tercih edin. `android.overridePathCheck=true` yalnızca
AGP önkontrolünü kaldırır. Cihaz testleri için API 26+ emülatör/cihaz gerekir:
`./gradlew :core:database:connectedDebugAndroidTest`.

## Test ve sürüm hazırlığı

Birim testleri sınıflandırma örneklerini, karşıt içerik kanıtlarını, metadata
fallback sırasını, cache süresini/bozulmasını, iptal yayılımını, istemci payload
eşlemelerini ve genel vitrin/Vault izolasyonunu kapsar. Diğer modüllerde model,
Room mapper, eklenti yönetimi, okuma ilerlemesi ve sync birleştirme testleri bulunur.

- Sürüm notları: [`release/RELEASE_NOTES.md`](release/RELEASE_NOTES.md).
- APK bütünlüğü: [`release/SHA256SUMS.txt`](release/SHA256SUMS.txt).
- Derleme/test raporu: [`release/VALIDATION.md`](release/VALIDATION.md) — 96 test geçti;
  lint: 0 hata, 86 uyarı; APK imzası doğrulandı.
- Bu hazırlık GitHub'da kendiliğinden tag veya release yayımlamaz.
- Üretim için kalıcı imzalama, cihaz smoke testi, OAuth ve sandbox host
  entegrasyonu tamamlanmalıdır.
