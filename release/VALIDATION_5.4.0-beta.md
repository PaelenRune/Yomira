# v5.4.0-beta derleme ve fiziksel cihaz doğrulaması

Tarih: 2026-10-27. Ortam: Windows, JDK 17, Android SDK / Build Tools 35.

## Derleme

- Komut: `gradlew.bat :app:assembleRelease --console=plain`
- Sonuç: **BUILD SUCCESSFUL**, release minify / R8 ve release lint vital başarılı.
- İlk çağrı 120 saniyelik araç zaman aşımına ulaştı; yeniden çağrı 1 dakika 35 saniyede başarıyla tamamlandı.
- Derleme kaydı: `C:\Users\paele\AppData\Local\Temp\opencode\yomira-5.4.0-release-build.log`.
- Önceden var olan deprecated Sort ikonları ve ExperimentalCoroutinesApi opt-in uyarıları gözlendi; derlemeyi engellemedi.

## APK bütünlüğü ve imza

- Dosya: `release/yomira-v5.4.0-beta.apk`
- Boyut: **10.978.918 bayt**.
- SHA-256: `c86a7b9959126d24b62e6e7245c8577a9601f225eb15e0352b42444d1ed6b38a`
- `aapt dump badging`: `com.yomira.app`, versionCode `9`, versionName `5.4.0-beta`, minSdk `26`, targetSdk `35`.
- `apksigner verify --verbose --print-certs`: **Verifies**, APK Signature Scheme v2.
- Sertifika: `CN=Yomira Release`, RSA 2048.
- Sertifika SHA-256: `0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.
- Mevcut `app/signing/yomira_release.keystore` kullanıldı; anahtar değiştirilmedi.

## Fiziksel cihaz

- `adb devices -l`: `b4bc1593 device product:apollo_global model:M2007J3SY device:apollo` — yetkili fiziksel cihaz.
- Model: Xiaomi M2007J3SY; Android **12**, SDK **31**.
- `adb -s b4bc1593 install -r release/yomira-v5.4.0-beta.apk`: **Success**; veri silme / uninstall yapılmadı.
- `adb shell am force-stop com.yomira.app` ardından `adb shell am start -W -n com.yomira.app/.MainActivity`: **Status: ok**, **LaunchState: COLD**, TotalTime **250 ms**.
- 15 saniye sonra `adb shell pidof com.yomira.app`: **29835**; takip kontrolünde aynı süreç canlı.
- `dumpsys package`: versionCode `9`, versionName `5.4.0-beta`.
- `dumpsys activity activities`: `mResumedActivity` = `com.yomira.app/.MainActivity`.
- `adb logcat -d -b crash --pid=29835`: boş; bu sürece ait crash kaydı yok.
- Süreç logunda `FATAL EXCEPTION`, `AndroidRuntime`, `Exception`, `Error` taraması: eşleşme yok.
- İlk zaman filtreli logcat çağrısı Windows argüman ayrıştırması nedeniyle reddedildi; log kontrolü süreç kimliği filtresiyle tekrarlandı ve başarılı oldu.

## Kapsam sınırı

Bu rapor imzalı release APK, mevcut kurulumun üzerine güncelleme ve temiz başlangıç smoke testini kapsar. Yeni birim test çalıştırması, tam UI regresyonu, tüm canlı sağlayıcılar ve uygulama içi APK güncellemesinin uçtan uca kurulumu bu görevde çalıştırılmadı. GitHub'a push / tag / release yayını yapılmadı.
