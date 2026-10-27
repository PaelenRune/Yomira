# Yomira v8.0.3-Beta

Version code: **17** · Android 8.0+ (API 26)

## İngilizce kapak önceliği

- Metadata kapakları kaynak kalitesinden önce dil kanıtına göre sıralanır. MangaDex'teki
  İngilizce cilt kapağı, AniList'teki orijinal Japonca kapağı her zaman geçer.
- Öncelik sırası: doğrulanmış İngilizce (`en`) ve ComicVine/OpenLibrary kapakları;
  kapak dili belirtilmeyen İngilizce başlıklı kayıtlar; dili bilinmeyen kapaklar;
  son olarak `ja`, `ko`, `zh` gibi İngilizce olmayan kapaklar.
- Açıkça İngilizce olmayan bir kapak dili, İngilizce seri başlığı veya Batılı kaynak
  ipucuyla yükseltilmez. Aynı dil seviyesinde mevcut kaynak kalite sırası korunur.
- MangaDex, ilişki sırasından bağımsız olarak kullanılabilir İngilizce `cover_art`
  seçer ve seçilen kapağın dilini metadata'ya taşır. İngilizce yoksa önce dili
  bilinmeyen, sonra İngilizce olmayan kapak kullanılır.
- Kapak tercihi arayüzün TR/EN dilinden bağımsızdır. Manzara/banner seçimi değişmez;
  geçersiz veya güvensiz görsel adresleri kullanılmaz.

**Sınır:** Bu sürüm metadata dil kanıtına göre önceliklendirme yapar; görüntü üzerinde OCR
uygulamaz. Kullanılabilir İngilizce kapak bulunmazsa orijinal kapak son çare olarak
kalır; her seri için İngilizce görsel veya sıfır Kanji garantisi verilemez.

## Verification

```powershell
.\gradlew.bat :data:metadata:test :core:network:test
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

JVM-only modüllerin testleri ayrıca çalıştırılır. Sonuçlar, APK sürüm/imza bilgileri
ve SHA-256 değerleri: [VALIDATION_v8.0.3-Beta.md](VALIDATION_v8.0.3-Beta.md).

Output APKs: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`.
