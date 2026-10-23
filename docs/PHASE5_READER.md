# Faz 5 — Evrensel okuyucu

## Kullanım

`:feature:reader`, `reader/{mangaId}/{chapterId}` rotasında açılır. Seri detayındaki
**Hemen oku** ve bölüm satırları bu rotayı kullanır. Vault erişim kontrolü okuyucuda
da korunur; cihaz kilitlendiğinde özel okuyucu rotası kaldırılır.

- Webtoon: aralıksız `LazyColumn`, yalnızca görünür/yakın sayfalar compose edilir.
- Manga: yatay RTL pager; Western: yatay LTR pager. Cihazın arayüz yönünden bağımsızdır.
- Tek dokunma kontrolleri açar/kapatır. Çift dokunma dokunulan noktaya 2,5× yakınlaşır
  veya sıfırlar. İki parmakla 1–5× yakınlaştırma yapılır; pan sayfa sınırlarında tutulur.
- OLED zemini gerçek `#000000` değeridir. Ters renkler yalnızca sayfa görsellerine
  uygulanır; HUD ve metinler ters çevrilmez.

## Sayfa ve bellek yaşam döngüsü

Güvenilir, kurulu kaynak köprüsü varsa `SourceBridge.getPageList` kullanılır.
Kaynak köprüsü yoksa HTTPS bölüm HTML'indeki bilinen okuyucu konteynerleri taranır;
kapak/reklam görsellerini rastgele sayfa olarak sunan genel `img` taraması yapılmaz.
JavaScript ile oluşturulan veya özel doğrulama isteyen kaynaklar kendi güvenilir
eklenti köprüsünü gerektirir; UI bu durumu yeniden deneme yönlendirmesiyle açıklar.
Kaynağın sayfa başlıkları (ör. Referer) görsel isteklerinde korunur.

Coil bitmap sahipliğini yönetir. Okuyucuya özel %20 bellek sınırına sahip LRU cache,
lazy item disposal ve en fazla 4096 piksel yüksekliğinde decode ile canlı bitmap
kullanımı sınırlandırılır. Disk görsel önbelleği kapalıdır. Compose çiziminin hâlâ
kullandığı bitmap'lerde elle `Bitmap.recycle()` çağrılmaz; modern Coil sürümünde
eski, manuel bitmap-pool API'si bulunmaz. Çok uzun görseller bu sınır için küçültülür;
bu sürüm özgün çözünürlüklü tile/subsampling yapmaz.

## İlerleme ve migration

Room v2, v1 kayıtlarını koruyan `MIGRATION_1_2` ile `reader_progress` tablosunu ekler.
Sayfa ve bölüm indeksleri sıfır tabanlıdır. Bölüm başına son sayfa saklanır; bölüm
listesinin yenilenmesi bu kayıtları silmez. ChapterDao ilerleme yazması ve SyncDao
`local-reader` dirty revision güncellemesi tek transaction içindedir. Son senkron
zamanı okuma sırasında değiştirilmez. Yazmalar sırayla işlenir, başarısız kayıt kullanıcıya
bildirilir. Okuma modu MangaDao ile seriye kaydedilir.

## Doğrulama

`ReaderProgressTest.kt`: yön mantığı, indeks sınırları, bölüm bazlı kayıt/geri yükleme,
geçersiz konumların reddi ve pan sınırları. Room cihaz testleri: v1→v2 migration,
bölüm yenilemesinden sonra konum korunması ve dirty revision artışı.

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest :core:database:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :core:database:connectedDebugAndroidTest
```

JDK 17 ve SDK 35 gereklidir. Türkçe karakterli checkout yollarındaki Gradle test
worker sorunu için kaynakları ASCII bir yola kopyalayarak test çalıştırın. Cihaz
testleri çalışan Android emülatörü/cihazı gerektirir.
