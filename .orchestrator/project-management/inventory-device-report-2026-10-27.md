# Inventory & Device Specialist raporu

Gözlem zamanı: 2026-10-27 11:55 +03:00. Kapsam: yerel model envanteri, bağlı Android cihaz ve mevcut APK; yeni build veya kurulum yapılmadı.

## Model envanteri

- Tam istenen `OpenChamber_Aktif_Modeller_Listesi(1).pdf` aranan Masaüstü dizinlerinde bulunamadı. Kullanılan dosya: `C:\Users\paele\Desktop\OpenChamber_Aktif_Modeller_Listesi.pdf`.
- Aynı dosya `C:\Users\paele\OneDrive\Desktop` altında da mevcut; iki kopyanın SHA-256 değerleri aynı.
- PDF: 4 sayfa, rapor tarihi 2026-10-20, 7 sağlayıcı / 90 sağlayıcı-model çifti.
- PDF dağılımı: google 7; evren 8; evren2 8; evren3 8; nvidia 9; openai 14; opencode 36.
- `opencode models` başarılı: 219 katalog kaydı. Bu sayı erişim, kota, kimlik doğrulama veya çıkarım başarısı anlamına gelmez.
- PDF ile CLI kesişimi 79 kayıt; PDF'deki 11 kayıt güncel CLI çıktısında yok. Tam farklar snapshot JSON içinde.
- Orkestratör modeli ve sağlayıcılar arası Gemini 3.8 Flash alias'ları kesinlikle subagent adayı değildir: `google/antigravity-gemini-3.8-flash`, `google/gemini-3.8-flash`, `opencode/gemini-3.8-flash`.
- PDF/CLI kesişiminden yasak alias'lar çıkarıldığında 77 envanter ön adayı kalır. Bunlar otomatik olarak kodlama ajanı kabul edilmemeli; görev yeteneği ve erişim ayrıca doğrulanmalıdır.
- `subagent-selection-policy.json` deny regex'i yeni sağlayıcılardaki aynı alias'ları da dışlar. Bu kayıt bir seçim politikasıdır; çalışan orkestratörün bunu yüklediği ayrıca doğrulanmadı.
- Önceden var olan `providers.json`, `endpoints.json`, `base-models.json` ve proje yönetimi dosyaları korunmuştur. Tarihsel kayıtlardaki Gemini 3.8 girdileri aday izni olarak kullanılmamalıdır.

## Cihaz ve kaldırma

- ADB PATH üzerinde yok; kullanılan ikili: `C:\Users\paele\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- `adb devices -l`: tek bağlı fiziksel cihaz `b4bc1593`, model `M2007J3SY`, ürün `apollo_global`, durum `device`. Emülatör gözlenmedi.
- `adb -s b4bc1593 shell pm list packages com.yomira.app`: işlem öncesi `package:com.yomira.app`.
- `dumpsys package`: `versionName=5.0.0`, `versionCode=6`, minSdk 26, targetSdk 35.
- Kullanıcının eski paketi kaldırma talimatı uygulandı: `adb -s b4bc1593 uninstall com.yomira.app` => `Success`.
- İşlem sonrası aynı paket sorgusu boş çıktı; paket kaldırılması doğrulandı. Yeni APK kurulmadı.

## APK durumu ve yayın engeli

| Dosya | Boyut (byte) | Dosya zamanı (yerel) |
| --- | ---: | --- |
| `release/yomira-v5.0.0.apk` | 3,350,274 | 2026-10-27 02:19:32 |
| `release/yomira-v5.1.0-beta.apk` | 10,733,878 | 2026-10-27 11:27:46 |

**Yayın engeli:** `aapt dump badging release/yomira-v5.1.0-beta.apk` manifestte `com.yomira.app`, `versionName='5.0.0'`, `versionCode='6'` bildiriyor. Dosya adı gerçek uygulama sürümünü yansıtmıyor. Sürüm bilgileri düzeltilip APK yeniden üretilmeden 5.1.0-beta olarak sunulmamalı.

- minSdk: 26; targetSdk: 35.
- ABI: arm64-v8a, armeabi-v7a, x86, x86_64.
- SHA-256: `9DA8AF5865F6693B3A5768670602ADCFCA2CD5598941ADFD1C1197F9F2143EA5`.
- Proje APK glob aramasında yalnızca yukarıdaki iki release APK'sı bulundu; başka APK build çıktısı bulunmadı.

## Üretilen kayıtlar

- `.orchestrator/model-registry/inventory-snapshot-2026-10-27.json`: PDF ve CLI tam listeleri, sağlayıcı sayıları, farklar, kaynak SHA-256.
- `.orchestrator/model-registry/inventory-pdf-extracted.txt`: PDF metni ve varyant bilgileri.
- `.orchestrator/model-registry/subagent-selection-policy.json`: yasak alias'lar ve 77 ön aday.
- `.orchestrator/model-registry/refresh-inventory.py`: mevcut snapshot'ı tekrar üreten yardımcı komut; dosya adındaki tarih bu keşif kaydına sabittir.

## Orkestratöre devir

1. Subagent seçmeden önce deny politikasını uygula; katalog varlığını başarılı model çağrısı sayma.
2. APK manifest/dosya adı sürüm uyumsuzluğunu build sorumlusuna devret.
3. Cihaz şu anda eski Yomira paketinden temizlenmiştir; düzeltilmiş APK'nın kurulumu bu görev kapsamında yapılmamıştır.
