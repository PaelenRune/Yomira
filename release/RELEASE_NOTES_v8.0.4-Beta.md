# Yomira v8.0.4-Beta

Version code: **18** · Android 8.0+ (API 26)

## Google hesabıyla Google Drive bağlantısı

- Ayarlar → Google Drive Senkronizasyonu bölümüne **Google Hesabı ile Giriş Yap**
  düğmesi ve yedek/okuma geçmişi açıklaması eklendi.
- Google hesabı ve app-data izni bağlanmadan manuel/otomatik eşitleme kullanılamaz.
- Bağlandıktan sonra hesap e-postası, otomatik/manuel eşitleme durumu ve
  **Çıkış Yap / Hesabı Ayır** düğmesi gösterilir.
- Hesap bağlantısı yeniden başlatmada korunur. Kısa ömürlü Drive erişim belirteçleri
  Google Play hizmetlerinden alınır; uygulamada token veya istemci sırrı saklanmaz.
- Hesabı ayırmak otomatik eşitlemeyi kapatır, yerel bağlantıyı kaldırır ve Google
  erişimini iptal etmeyi dener. Drive yedekleri ve yerel kitaplık silinmez.
- TR/EN metinleri, iptal/hata durumları ve kimlik doğrulama testleri eklendi.

**Dağıtım gereksinimi:** Google Cloud'da Drive API, OAuth onay ekranı ve Android
paket/imza kaydı yapılandırılmalıdır. Bu depodan Cloud yapılandırması ve gerçek hesapla
uçtan uca giriş doğrulanamaz. Kurulum ve cihaz kontrol listesi:
[GOOGLE_DRIVE_AUTH.md](../docs/GOOGLE_DRIVE_AUTH.md).

## Verification

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest :app:testDebugUnitTest assembleDebug assembleRelease
```

APKs: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`.
Build/test results and checksums: [VALIDATION_v8.0.4-Beta.md](VALIDATION_v8.0.4-Beta.md).
