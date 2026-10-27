<div align="center">

<img src="art/yomira_logo.png" alt="Yomira logo" width="160" />

# Yomira

*Free and open source multiplatform manga and comic reader for Android and Windows.*

Manga, manhwa, webtoon ve Batı çizgi romanları için tek kütüphane, tek OLED Plum tasarım dili —
Android ve Windows'ta.

[![Release v5.1.0](https://img.shields.io/github/v/release/PaelenRune/Yomira?style=flat-square&label=Release&labelColor=13141C&color=9D4EDD)](https://github.com/PaelenRune/Yomira/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&labelColor=13141C&logo=android&logoColor=white)](https://github.com/PaelenRune/Yomira/releases/latest)
[![Windows 10/11](https://img.shields.io/badge/Windows-10%2F11-0078D4?style=flat-square&labelColor=13141C&logo=windows&logoColor=white)](https://github.com/PaelenRune/Yomira/releases/latest)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-4285F4?style=flat-square&labelColor=13141C&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-6366F1?style=flat-square&labelColor=13141C)](LICENSE)

[Hızlı İndirme](#-hızlı-indirme-download) · [Ekran Görüntüleri](#-ekran-görüntüleri) · [Özellikler](#-özellikler) · [Derleme](#-derleme) · [Sürüm Notları](release/RELEASE_NOTES.md)

</div>

## 📥 Hızlı İndirme (Download)

| Platform | İndirme | Boyut |
| :--- | :--- | ---: |
| 🤖 **Android · Universal** | [`yomira-v5.1.0.apk`](https://github.com/PaelenRune/Yomira/releases/latest/download/yomira-v5.1.0.apk) | ~24.8 MB |
| 🤖 **Android · arm64-v8a** | [`yomira-arm64-v8a-v5.1.0.apk`](https://github.com/PaelenRune/Yomira/releases/latest/download/yomira-arm64-v8a-v5.1.0.apk) | ~24.5 MB |
| 🖥️ **Windows · taşınabilir** | [`Yomira-5.1.0.exe`](https://github.com/PaelenRune/Yomira/releases/latest/download/Yomira-5.1.0.exe) | — |
| 🖥️ **Windows · kurulum** | [`Yomira-5.1.0.msi`](https://github.com/PaelenRune/Yomira/releases/latest/download/Yomira-5.1.0.msi) | — |

> **Gereksinimler:** Android 8.0+ (API 26) · Windows 10/11 (x64).
> Diğer ABI paketleri (`armeabi-v7a`, `x86_64`) ve [`SHA256SUMS.txt`](https://github.com/PaelenRune/Yomira/releases/latest/download/SHA256SUMS.txt)
> için [tüm asset'lere](https://github.com/PaelenRune/Yomira/releases/latest) bak. Aynı imza
> anahtarı sayesinde yeni sürüm eski kurulumun **üzerine** kurulur; kütüphane ve ilerleme korunur.

## 📸 Ekran Görüntüleri

Android görselleri **Xiaomi Mi 10T** gerçek cihazından, Windows görseli masaüstü derlemesinden
alınmıştır — mockup değildir.

<div align="center">

<img src="art/screenshots/real_phone_home.png" alt="Ana raf — OLED Plum, gerçek cihaz" width="230" /> &nbsp;
<img src="art/screenshots/real_phone_detail.png" alt="Seri detayı — dile duyarlı kaynak eşleştirme" width="230" />

<sub><b>Android</b> · Xiaomi Mi 10T — ana raf ve dile duyarlı kaynak eşleştirmeli seri detayı</sub>

<br/>
<br/>

<img src="art/screenshot_desktop_browse.png" alt="Windows masaüstü — katalog tarama" width="700" />

<sub><b>Windows</b> · katalog tarama — aynı kütüphane, aynı tema</sub>

</div>

## ✨ Özellikler

- 🌐 **Dile duyarlı akıllı kaynak eşleştirme (Smart Matching)** — Onboarding sihirbazında veya
  ayarlarda seçilen aktif dil (TR/EN) birincil sıralama anahtarı olarak kullanılır. Doğru dildeki
  kaynaklar en başa yerleşir, uyumsuz tek dilli kaynaklar filtrelenir.
- 📖 **Gelişmiş Okuma Deneyimi** — Dikişsiz webtoon akışı, manga sağdan sola modu ve 120 Hz
  yenileme hızında akıcı kaydırma.
- 🎨 **Saf OLED Plum Teması** — Pil tasarruflu koyu siyah arka plan ve mor aksanlar; tüm
  platformlarda tek palet.
- 🔒 **Gizli Kasa (Vault)** — Biyometrik veya PIN ile kilitlenebilen özel arşiv; kapalıyken
  arayüzde iz bırakmaz.
- 🔄 **Yedekleme & İlerleme** — Otomatik okuma geçmişi, kaldığın yerden devam ve yerel
  yedekleme (Mihon / Tachiyomi `.tachibk` uyumlu).

## 🔨 Derleme

**Gereksinimler:** JDK 17 · Android SDK 35 · Gradle wrapper.

```bash
# Android — universal + ABI bazlı imzalı APK'lar
./gradlew assembleRelease

# Windows — EXE ve MSI
./gradlew :desktop:packageDistributionForCurrentOS
```

Çıktılar: `app/build/outputs/apk/release/` ve `desktop/build/compose/binaries/main/{exe,msi}/`.
Release derlemeleri R8 tam modda çalışır; Room, Hilt, kotlinx.serialization ve WorkManager
sınıfları `app/proguard-rules.pro` içinde korunur.

## 📄 Lisans

[Apache License 2.0](LICENSE) · © 2026 PaelenRune

<sub>Yomira bölüm barındırmaz veya dağıtmaz; yalnızca erişim hakkın bulunan içerikleri kullan.</sub>
