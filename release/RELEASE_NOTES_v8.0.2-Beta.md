# Yomira v8.0.2-Beta

Version code: **16** · Android 8.0+ (API 26)

## Eklenti yönetimi

- Ana ekran üst çubuğundan veya **Ayarlar → Eklentiler** üzerinden açılan yeni Mihon tarzı eklenti ekranı.
- Aktif depoları görüntüleme, HTTPS dizin adresiyle özel depo ekleme ve depo kaldırma.
- Eklenti/kaynak adına göre arama; Tümü, Yüklü, Güncelleme ve TR/EN/tüm diller filtreleri.
- Sürüm, dil ve +18/NSFW bilgileri; yükleme, güncelleme ve kaldırma sırasında işlem göstergesi.
- Kurulumlar mevcut JSON/sandbox yükleyicisini kullanır; Android paket yükleyicisi açılmaz. Yerleşik MangaDex kaldırılamaz.
- Deposu kaldırılan veya çevrimdışı kalan yüklü eklentiler listelenmeye ve kaldırılmaya devam eder.
- Normal ve boyutu önceden bilinmeyen APK indirmelerini yanlışlıkla reddeden boyut kontrolü düzeltildi; indirme sınırı korunur.
- Yeni ekranın metinleri Türkçe ve İngilizce sunulur.

## Kullanım

1. **Eklentiler → + Depo Ekle**: güvendiğin deponun HTTPS JSON dizini adresini ekle.
2. İsim veya kaynak ara; dil filtresini seç.
3. **Yükle**, **Güncelle** veya **Kaldır** düğmesini kullan.
4. Depo kaldırmak kurulu eklentileri silmez. Yalnızca güvendiğin depoları kullan.

## Verification

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
# JVM-only extension engine tests (not included in testDebugUnitTest):
.\gradlew.bat :data:extension:test
```

Output APKs: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`.
